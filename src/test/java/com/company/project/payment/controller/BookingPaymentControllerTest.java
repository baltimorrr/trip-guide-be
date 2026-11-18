package com.company.project.payment.controller;

import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.company.project.booking.controller.BookingClient;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.FakePaymentGateway;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Starting and reading a booking's card payment (C-PAY-01/02): one PaymentIntent per booking for its total, the same
 * client secret on every start while it can be paid, and nothing to start for a booking that can't be paid.
 */
@IntegrationTest
class BookingPaymentControllerTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(40);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakePaymentGateway stripe;

	CatalogFixtures catalog;

	BookingClient client;

	long ratePlan;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		// A country no tax rule is ever written for, so the total is the room price.
		long room = catalog.roomType(catalog.hotel("Paying " + uniqueSuffix()).country("XP").create().id(), 2, 0, 2, 5);
		ratePlan = catalog.defaultRatePlan(room);
		client = BookingClient.signUp(mvc, catalog);
	}

	@Test
	void startingGivesTheClientSecretOfOneIntentForTheBookingsTotal() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);

		MvcTestResult started = client.startPayment(reference);

		assertThat(started).hasStatusOk();
		String json = body(started);
		String intentId = stripe.intentIdOf(reference);
		assertThat(JsonPath.<String>read(json, "$.clientSecret")).isEqualTo(stripe.intent(intentId).clientSecret());
		assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("PENDING");
		assertThat(JsonPath.<String>read(json, "$.paymentStatus")).isEqualTo("REQUIRES_PAYMENT");
		assertThat(BookingClient.money(JsonPath.read(json, "$.amount"))).isEqualTo(2 * DEFAULT_PRICE);
		assertThat(Instant.parse(JsonPath.read(json, "$.payBy"))).isEqualTo(holdOf(reference));
		assertThat(stripe.intent(intentId).amountMinor()).isEqualTo(2 * DEFAULT_PRICE);
		assertThat(stripe.intent(intentId).currency()).isEqualTo("VND");
		assertThat(payments(reference)).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("stripe_payment_intent_id", intentId)
				.containsEntry("status", "REQUIRES_PAYMENT")
				.containsEntry("amount_minor", 2 * DEFAULT_PRICE)
				.containsEntry("currency", "VND"));

		MvcTestResult again = client.startPayment(reference);

		assertThat(again).hasStatusOk();
		assertThat(JsonPath.<String>read(body(again), "$.clientSecret")).isEqualTo(stripe.intent(intentId).clientSecret());
		assertThat(stripe.intentCreates(reference)).isEqualTo(1);
		assertThat(payments(reference)).hasSize(1);
	}

	@Test
	void aDeclinedCardIsRetriedOnTheSameIntent() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);
		assertThat(client.startPayment(reference)).hasStatusOk();
		String intentId = stripe.intentIdOf(reference);
		stripe.decline(intentId, "Your card was declined.");

		MvcTestResult retry = client.startPayment(reference);

		assertThat(retry).hasStatusOk();
		assertThat(JsonPath.<String>read(body(retry), "$.clientSecret")).isEqualTo(stripe.intent(intentId).clientSecret());
		assertThat(stripe.intentCreates(reference)).isEqualTo(1);
	}

	@Test
	void twoStartsAtOnceCreateOneIntent() throws Exception {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			List<Future<MvcTestResult>> calls = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				calls.add(threads.submit(() -> {
					go.await();
					return client.startPayment(reference);
				}));
			}
			go.countDown();
			for (Future<MvcTestResult> call : calls) {
				assertThat(call.get()).hasStatusOk();
			}
		}
		finally {
			threads.shutdown();
		}

		assertThat(payments(reference)).hasSize(1);
	}

	@Test
	void anotherUsersBookingOrAnUnknownOneIsNotFound() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);
		BookingClient stranger = BookingClient.signUp(mvc, catalog);

		assertThat(stranger.startPayment(reference)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(stranger.payment(reference)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(client.startPayment("HB23456789")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(stripe.intentIdOf(reference)).isNull();
	}

	@Test
	void aBookingWhoseHoldEndedCantBePaid() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);
		jdbc.sql("update bookings set hold_expires_at = now() - interval '1 second' where reference = :reference")
			.param("reference", reference)
			.update();

		MvcTestResult started = client.startPayment(reference);

		assertThat(started).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(started), "$.detail"))
			.isEqualTo("The rooms are no longer held for this booking. Book them again");
		assertThat(stripe.intentIdOf(reference)).isNull();
	}

	@Test
	void anExpiredOrConfirmedBookingCantBePaid() {
		String expired = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);
		String confirmed = client.bookRooms(ratePlan, CHECK_OUT, CHECK_OUT.plusDays(1), 1, null);
		setStatus(expired, "EXPIRED", "");
		setStatus(confirmed, "CONFIRMED", ", confirmed_at = now()");

		assertThat(client.startPayment(expired)).hasStatus(HttpStatus.CONFLICT);
		MvcTestResult paid = client.startPayment(confirmed);
		assertThat(paid).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(paid), "$.detail")).isEqualTo("This booking is already paid");
	}

	@Test
	void aBookingWithNothingToPayIsConfirmedWithoutStripe() {
		String code = "free-" + uniqueSuffix();
		catalog.discountCode(code).percent("100", null, null).create();
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, code);

		MvcTestResult started = client.startPayment(reference);

		assertThat(started).hasStatusOk();
		assertThat(JsonPath.<String>read(body(started), "$.status")).isEqualTo("CONFIRMED");
		assertThat(JsonPath.<String>read(body(started), "$.clientSecret")).isNull();
		assertThat(stripe.intentCreates(reference)).isZero();
		assertThat(jdbc.sql("""
				select h.reason from booking_status_history h join bookings b on b.id = h.booking_id
				where b.reference = :reference and h.to_status = 'CONFIRMED'""")
			.param("reference", reference)
			.query(String.class)
			.single()).isEqualTo("Nothing to pay");
	}

	@Test
	void whenStripeIsDownNothingIsWritten() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);
		stripe.failNextCall();

		MvcTestResult started = client.startPayment(reference);

		assertThat(started).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(payments(reference)).isEmpty();
	}

	@Test
	void theStatusShowsThePaymentOnceStarted() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);

		String before = body(client.payment(reference));
		assertThat(JsonPath.<String>read(before, "$.bookingStatus")).isEqualTo("PENDING");
		assertThat(JsonPath.<Object>read(before, "$.payment")).isNull();
		assertThat(Instant.parse(JsonPath.read(before, "$.payBy"))).isEqualTo(holdOf(reference));

		client.startPayment(reference);

		String after = body(client.payment(reference));
		assertThat(JsonPath.<String>read(after, "$.payment.status")).isEqualTo("REQUIRES_PAYMENT");
		assertThat(JsonPath.<Object>read(after, "$.payment.failureReason")).isNull();
	}

	private List<Map<String, Object>> payments(String reference) {
		return jdbc.sql("""
				select p.stripe_payment_intent_id, p.status, p.amount_minor, p.currency
				from payments p join bookings b on b.id = p.booking_id where b.reference = :reference""")
			.param("reference", reference)
			.query()
			.listOfRows();
	}

	private Instant holdOf(String reference) {
		return jdbc.sql("select hold_expires_at from bookings where reference = :reference")
			.param("reference", reference)
			.query(Instant.class)
			.single();
	}

	// Walks the booking along a lifecycle arrow, as the triggers require.
	private void setStatus(String reference, String status, String extraColumns) {
		jdbc.sql("update bookings set status = :status" + extraColumns + " where reference = :reference")
			.param("status", status)
			.param("reference", reference)
			.update();
	}

}
