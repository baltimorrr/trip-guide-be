package com.company.project.booking.controller;

import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.company.project.payment.services.RefundService;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.FakePaymentGateway;
import com.company.project.support.IntegrationTest;
import com.company.project.support.RecordingBookingMailService;
import com.company.project.support.StripeWebhooks;
import com.jayway.jsonpath.JsonPath;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cancelling a paid booking (C-MYBK-03) and its refund (C-PAY-05): the preview's penalty and refund, the rooms back on
 * sale, the refund on Stripe with its status on the booking, and the emails; only Confirmed bookings, only by their
 * own user, only at the refund the customer saw.
 */
@IntegrationTest
class BookingControllerCancelTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(20);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakePaymentGateway stripe;

	@Autowired
	RefundService refunds;

	CatalogFixtures catalog;

	BookingClient client;

	long hotelId;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		hotelId = catalog.hotel("Cancel " + uniqueSuffix()).country("XP").create().id();
		client = BookingClient.signUp(mvc, catalog);
	}

	@Test
	void aFreeCancellationGivesBackTheRoomsAndEverythingPaid() {
		long room = catalog.roomType(hotelId, 2, 0, 2, 5);
		String reference = client.bookRooms(catalog.defaultRatePlan(room), CHECK_IN, CHECK_OUT, 2, null);
		client.pay(reference, stripe);
		assertThat(bookedRooms(room)).containsExactly(2, 2);

		String quote = body(client.get(cancellationOf(reference)));
		assertThat(BookingClient.money(JsonPath.read(quote, "$.paid"))).isEqualTo(4 * DEFAULT_PRICE);
		assertThat(BookingClient.money(JsonPath.read(quote, "$.penalty"))).isZero();
		assertThat(BookingClient.money(JsonPath.read(quote, "$.refund"))).isEqualTo(4 * DEFAULT_PRICE);

		MvcTestResult cancelled = cancel(reference, refundOf(quote), " Plans changed ");

		assertThat(cancelled).hasStatusOk();
		String json = body(cancelled);
		assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("CANCELLED");
		assertThat(JsonPath.<String>read(json, "$.cancellation.reason")).isEqualTo("Plans changed");
		assertThat(BookingClient.money(JsonPath.read(json, "$.cancellation.refund.amount"))).isEqualTo(4 * DEFAULT_PRICE);
		assertThat(bookedRooms(room)).containsExactly(0, 0);
		assertThat(lastStatusChange(reference))
			.isEqualTo(tuple("CONFIRMED", "CANCELLED", client.userId, "Cancelled by customer: Plans changed"));
		String refundId = stripe.refundIdOf(reference);
		assertThat(stripe.refundAmount(refundId)).isEqualTo(4 * DEFAULT_PRICE);
		assertThat(refundRow(reference)).containsEntry("status", "SUCCEEDED")
			.containsEntry("stripe_refund_id", refundId)
			.containsEntry("initiated_by", client.userId);
		String found = body(client.get("/api/bookings/" + reference));
		assertThat(JsonPath.<String>read(found, "$.cancellation.refund.status")).isEqualTo("SUCCEEDED");
		assertThat(JsonPath.<List<String>>read(found, "$.rooms[*].cancellation.policyName")).hasSize(2);
		assertThat(RecordingBookingMailService.mails(reference, "cancellation")).singleElement()
			.satisfies(mail -> assertThat(mail.booking().cancellation().refund().amount().amount())
				.isEqualByComparingTo(String.valueOf(4 * DEFAULT_PRICE)));
		assertThat(RecordingBookingMailService.mails(reference, "owner-cancellation")).hasSize(1);
	}

	@Test
	void aPenaltyKeepsItsShareOfWhatWasPaid() {
		// Check-in is 20 days away, so "half within 1000 hours" (about 41 days) is in effect now.
		long policy = catalog.cancellationPolicy("Half within 1000 hours " + uniqueSuffix(), true);
		catalog.cancellationRule(policy, 1000, "PERCENT", "50");
		long plan = catalog.ratePlan(catalog.bareRoomType(hotelId, 2, 0, 2, 5)).policy(policy).create();
		String reference = client.bookRooms(plan, CHECK_IN, CHECK_OUT, 1, null);
		client.pay(reference, stripe);

		String quote = body(client.get(cancellationOf(reference)));
		assertThat(BookingClient.money(JsonPath.read(quote, "$.penalty"))).isEqualTo(DEFAULT_PRICE);
		assertThat(BookingClient.money(JsonPath.read(quote, "$.refund"))).isEqualTo(DEFAULT_PRICE);

		assertThat(cancel(reference, refundOf(quote), null)).hasStatusOk();
		assertThat(refundRow(reference)).containsEntry("amount_minor", DEFAULT_PRICE);
		assertThat(jdbc.sql("select cancellation_fee_minor from bookings where reference = :reference")
			.param("reference", reference)
			.query(Long.class)
			.single()).isEqualTo(DEFAULT_PRICE);
	}

	@Test
	void aNonRefundableRateIsCancelledWithoutARefund() {
		long policy = catalog.cancellationPolicy("Non-refundable " + uniqueSuffix(), false);
		catalog.cancellationRule(policy, 100_000, "PERCENT", "100");
		long room = catalog.bareRoomType(hotelId, 2, 0, 2, 5);
		String reference = client.bookRooms(catalog.ratePlan(room).policy(policy).create(), CHECK_IN, CHECK_OUT, 1,
				null);
		client.pay(reference, stripe);

		String quote = body(client.get(cancellationOf(reference)));
		assertThat(BookingClient.money(JsonPath.read(quote, "$.refund"))).isZero();

		MvcTestResult cancelled = cancel(reference, refundOf(quote), null);

		assertThat(cancelled).hasStatusOk();
		assertThat(JsonPath.<Object>read(body(cancelled), "$.cancellation.refund")).isNull();
		assertThat(refundRow(reference)).isNull();
		assertThat(stripe.refundIdOf(reference)).isNull();
		assertThat(bookedRooms(room)).containsExactly(0, 0);
	}

	@Test
	void aRefundThatChangedSinceThePreviewMustBeConfirmedFirst() {
		long room = catalog.roomType(hotelId, 2, 0, 2, 5);
		String reference = client.bookRooms(catalog.defaultRatePlan(room), CHECK_IN, CHECK_OUT, 1, null);
		client.pay(reference, stripe);

		MvcTestResult stale = cancel(reference, "{\"amount\": 1, \"currency\": \"VND\"}", null);

		assertThat(stale).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(stale), "$.detail"))
			.isEqualTo("The refund is now %d VND. Check it and confirm".formatted(2 * DEFAULT_PRICE));
		assertThat(status(reference)).isEqualTo("CONFIRMED");
		assertThat(bookedRooms(room)).containsExactly(1, 1);
		assertThat(refundRow(reference)).isNull();
	}

	@Test
	void onlyAConfirmedBookingOfYourOwnCanBeCancelled() {
		long plan = catalog.defaultRatePlan(catalog.roomType(hotelId, 2, 0, 2, 5));
		String pending = client.bookRooms(plan, CHECK_IN, CHECK_OUT, 1, null);
		String checkedIn = client.bookRooms(plan, CHECK_OUT, CHECK_OUT.plusDays(1), 1, null);
		client.pay(checkedIn, stripe);
		jdbc.sql("update bookings set status = 'CHECKED_IN', checked_in_at = now() where reference = :reference")
			.param("reference", checkedIn)
			.update();
		String paid = client.bookRooms(plan, CHECK_OUT.plusDays(1), CHECK_OUT.plusDays(2), 1, null);
		client.pay(paid, stripe);
		String refund = refundOf(body(client.get(cancellationOf(paid))));

		MvcTestResult unpaid = client.get(cancellationOf(pending));
		assertThat(unpaid).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(unpaid), "$.detail"))
			.isEqualTo("An unpaid booking can't be cancelled. Its rooms are released when the hold ends");
		assertThat(cancel(pending, refund, null)).hasStatus(HttpStatus.CONFLICT);
		assertThat(cancel(checkedIn, refund, null)).hasStatus(HttpStatus.CONFLICT);
		assertThat(BookingClient.signUp(mvc, catalog).post("/api/bookings/" + paid + "/cancel",
				"{\"expectedRefund\": " + refund + "}")).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(cancel(paid, refund, null)).hasStatusOk();
		MvcTestResult again = cancel(paid, refund, null);
		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(again), "$.detail")).isEqualTo("This booking is already cancelled");
	}

	@Test
	void aRefundThatDidntReachStripeIsSentByTheRetryAndGivenUpAfterADay() {
		long plan = catalog.defaultRatePlan(catalog.roomType(hotelId, 2, 0, 2, 5));
		String late = client.bookRooms(plan, CHECK_IN, CHECK_OUT, 1, null);
		String lost = client.bookRooms(plan, CHECK_OUT, CHECK_OUT.plusDays(1), 1, null);
		client.pay(late, stripe);
		client.pay(lost, stripe);
		String lateRefund = refundOf(body(client.get(cancellationOf(late))));
		String lostRefund = refundOf(body(client.get(cancellationOf(lost))));

		stripe.failNextCall();
		assertThat(cancel(late, lateRefund, null)).hasStatusOk();
		stripe.failNextCall();
		assertThat(cancel(lost, lostRefund, null)).hasStatusOk();

		assertThat(refundRow(late)).containsEntry("status", "PENDING").containsEntry("stripe_refund_id", null);
		assertThat(JsonPath.<String>read(body(client.get("/api/bookings/" + late)), "$.cancellation.refund.status"))
			.isEqualTo("PENDING");
		ageRefund(late, "2 minutes");
		ageRefund(lost, "24 hours");

		refunds.retryUnsent();

		assertThat(refundRow(late)).containsEntry("status", "SUCCEEDED")
			.containsEntry("stripe_refund_id", stripe.refundIdOf(late));
		assertThat(refundRow(lost)).containsEntry("status", "FAILED").containsEntry("stripe_refund_id", null);
		assertThat(stripe.refundIdOf(lost)).isNull();
	}

	@Test
	void aRefundStripeReportsFailedShowsAsFailed() {
		String reference = client.bookRooms(catalog.defaultRatePlan(catalog.roomType(hotelId, 2, 0, 2, 5)), CHECK_IN,
				CHECK_OUT, 1, null);
		client.pay(reference, stripe);
		assertThat(cancel(reference, refundOf(body(client.get(cancellationOf(reference)))), null)).hasStatusOk();
		String refundId = stripe.refundIdOf(reference);

		stripe.failRefund(refundId);
		assertThat(StripeWebhooks.send(mvc, "refund.failed", refundId)).hasStatusOk();

		assertThat(JsonPath.<String>read(body(client.get("/api/bookings/" + reference)), "$.cancellation.refund.status"))
			.isEqualTo("FAILED");
	}

	@Test
	void twoCancelsAtOnceCancelAndRefundOnce() throws Exception {
		String reference = client.bookRooms(catalog.defaultRatePlan(catalog.roomType(hotelId, 2, 0, 2, 5)), CHECK_IN,
				CHECK_OUT, 1, null);
		client.pay(reference, stripe);
		String refund = refundOf(body(client.get(cancellationOf(reference))));
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService threads = Executors.newFixedThreadPool(2);
		List<Integer> statuses = new ArrayList<>();
		try {
			List<Future<MvcTestResult>> calls = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				calls.add(threads.submit(() -> {
					go.await();
					return cancel(reference, refund, null);
				}));
			}
			go.countDown();
			for (Future<MvcTestResult> call : calls) {
				statuses.add(call.get().getResponse().getStatus());
			}
		}
		finally {
			threads.shutdown();
		}

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(jdbc.sql("""
				select count(*) from refunds r join bookings b on b.id = r.booking_id where b.reference = :reference""")
			.param("reference", reference)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	private MvcTestResult cancel(String reference, String expectedRefund, String reason) {
		return client.post("/api/bookings/" + reference + "/cancel",
				"{\"expectedRefund\": %s, \"reason\": %s}".formatted(expectedRefund, BookingClient.json(reason)));
	}

	private static String cancellationOf(String reference) {
		return "/api/bookings/" + reference + "/cancellation";
	}

	private static String refundOf(String quote) {
		Map<String, Object> refund = JsonPath.read(quote, "$.refund");
		return "{\"amount\": %s, \"currency\": \"%s\"}".formatted(refund.get("amount"), refund.get("currency"));
	}

	private String status(String reference) {
		return jdbc.sql("select status from bookings where reference = :reference")
			.param("reference", reference)
			.query(String.class)
			.single();
	}

	private Map<String, Object> refundRow(String reference) {
		return jdbc.sql("""
				select r.status, r.stripe_refund_id, r.amount_minor, r.initiated_by
				from refunds r join bookings b on b.id = r.booking_id where b.reference = :reference""")
			.param("reference", reference)
			.query()
			.listOfRows()
			.stream()
			.findFirst()
			.orElse(null);
	}

	private void ageRefund(String reference, String age) {
		jdbc.sql("""
				update refunds set created_at = now() - cast(:age as interval)
				where booking_id = (select id from bookings where reference = :reference)""")
			.param("age", age)
			.param("reference", reference)
			.update();
	}

	private Tuple lastStatusChange(String reference) {
		Map<String, Object> row = jdbc.sql("""
				select h.from_status, h.to_status, h.changed_by, h.reason
				from booking_status_history h join bookings b on b.id = h.booking_id
				where b.reference = :reference order by h.id desc limit 1""")
			.param("reference", reference)
			.query()
			.singleRow();
		return tuple(row.get("from_status"), row.get("to_status"), row.get("changed_by"), row.get("reason"));
	}

	private List<Integer> bookedRooms(long roomTypeId) {
		return jdbc.sql("select booked_rooms from room_inventory where room_type_id = :id order by stay_date")
			.param("id", roomTypeId)
			.query(Integer.class)
			.list();
	}

}
