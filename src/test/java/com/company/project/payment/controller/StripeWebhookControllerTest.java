package com.company.project.payment.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.company.project.booking.controller.BookingClient;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.FakePaymentGateway;
import com.company.project.support.IntegrationTest;
import com.company.project.support.StripeWebhooks;
import com.jayway.jsonpath.JsonPath;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Stripe's webhook (C-PAY-01/02): only a signed event changes anything; a success confirms the Pending booking, a
 * decline keeps it Pending with Stripe's message; repeats, late reports and unknown intents change nothing more.
 */
@IntegrationTest
class StripeWebhookControllerTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(45);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakePaymentGateway stripe;

	BookingClient client;

	String reference;

	String intentId;

	@BeforeEach
	void setUp() {
		CatalogFixtures catalog = new CatalogFixtures(jdbc);
		long room = catalog.roomType(catalog.hotel("Webhook " + uniqueSuffix()).country("XP").create().id(), 2, 0, 2, 5);
		client = BookingClient.signUp(mvc, catalog);
		reference = client.bookRooms(catalog.defaultRatePlan(room), CHECK_IN, CHECK_IN.plusDays(2), 1, null);
		assertThat(client.startPayment(reference)).hasStatusOk();
		intentId = stripe.intentIdOf(reference);
	}

	@Test
	void aSucceededIntentConfirmsTheBooking() {
		stripe.succeed(intentId);

		assertThat(StripeWebhooks.send(mvc, "payment_intent.succeeded", intentId)).hasStatusOk();

		assertThat(booking()).containsEntry("status", "CONFIRMED").containsEntry("confirmed", true);
		assertThat(payment()).containsEntry("status", "SUCCEEDED").containsEntry("paid", true);
		assertThat(history()).containsExactly(tuple(null, "PENDING", client.userId, null),
				tuple("PENDING", "CONFIRMED", null, "Paid by card"));
		String status = body(client.payment(reference));
		assertThat(JsonPath.<String>read(status, "$.bookingStatus")).isEqualTo("CONFIRMED");
		assertThat(JsonPath.<String>read(status, "$.payment.status")).isEqualTo("SUCCEEDED");
		assertThat(JsonPath.<String>read(status, "$.payment.paidAt")).isNotNull();
		assertThat(JsonPath.<Object>read(status, "$.payBy")).isNull();
	}

	@Test
	void aBadOrMissingSignatureChangesNothing() {
		stripe.succeed(intentId);
		String payload = StripeWebhooks.event("payment_intent.succeeded", intentId);
		String eventId = JsonPath.read(payload, "$.id");

		MvcTestResult tampered = mvc.post()
			.uri("/api/payments/stripe/webhook")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Stripe-Signature", StripeWebhooks.sign(payload, "whsec_someone_else", Instant.now()))
			.content(payload)
			.exchange();
		MvcTestResult unsigned = mvc.post()
			.uri("/api/payments/stripe/webhook")
			.contentType(MediaType.APPLICATION_JSON)
			.content(payload)
			.exchange();

		assertThat(tampered).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(unsigned).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(booking()).containsEntry("status", "PENDING");
		assertThat(eventRow(eventId)).isNull();
	}

	@Test
	void theSameEventTwiceConfirmsOnce() {
		stripe.succeed(intentId);
		String payload = StripeWebhooks.event("payment_intent.succeeded", intentId);

		assertThat(StripeWebhooks.send(mvc, payload)).hasStatusOk();
		assertThat(StripeWebhooks.send(mvc, payload)).hasStatusOk();
		// Stripe may also send the same change as a new event.
		assertThat(StripeWebhooks.send(mvc, "payment_intent.succeeded", intentId)).hasStatusOk();

		assertThat(history()).hasSize(2);
		assertThat(eventRow(JsonPath.read(payload, "$.id"))).containsEntry("processed", true).containsEntry("error", null);
	}

	@Test
	void aDeclinedCardKeepsTheBookingPendingUntilARetrySucceeds() {
		stripe.decline(intentId, "Your card was declined.");

		assertThat(StripeWebhooks.send(mvc, "payment_intent.payment_failed", intentId)).hasStatusOk();

		assertThat(booking()).containsEntry("status", "PENDING");
		String declined = body(client.payment(reference));
		assertThat(JsonPath.<String>read(declined, "$.payment.status")).isEqualTo("FAILED");
		assertThat(JsonPath.<String>read(declined, "$.payment.failureReason")).isEqualTo("Your card was declined.");
		assertThat(JsonPath.<String>read(declined, "$.payBy")).isNotNull();

		assertThat(client.startPayment(reference)).hasStatusOk();
		stripe.succeed(intentId);
		assertThat(StripeWebhooks.send(mvc, "payment_intent.succeeded", intentId)).hasStatusOk();

		assertThat(booking()).containsEntry("status", "CONFIRMED");
		assertThat(payment()).containsEntry("status", "SUCCEEDED").containsEntry("failure_reason", null);
	}

	@Test
	void aDeclineReportedAfterTheSuccessKeepsItPaid() {
		stripe.succeed(intentId);
		assertThat(StripeWebhooks.send(mvc, "payment_intent.succeeded", intentId)).hasStatusOk();
		// As if Stripe answered with an older state.
		stripe.decline(intentId, "Your card was declined.");

		assertThat(StripeWebhooks.send(mvc, "payment_intent.payment_failed", intentId)).hasStatusOk();

		assertThat(booking()).containsEntry("status", "CONFIRMED");
		assertThat(payment()).containsEntry("status", "SUCCEEDED");
	}

	@Test
	void aPaymentForABookingNoLongerPendingIsRecordedButConfirmsNothing() {
		jdbc.sql("update bookings set status = 'EXPIRED' where reference = :reference")
			.param("reference", reference)
			.update();
		stripe.succeed(intentId);

		assertThat(StripeWebhooks.send(mvc, "payment_intent.succeeded", intentId)).hasStatusOk();

		assertThat(booking()).containsEntry("status", "EXPIRED").containsEntry("confirmed", false);
		assertThat(payment()).containsEntry("status", "SUCCEEDED");
	}

	@Test
	void anIntentNoBookingStartedIsIgnored() {
		String foreign = stripe.createPaymentIntent("HBFOREIGN" + uniqueSuffix(), 1_000, "VND").id();

		assertThat(StripeWebhooks.send(mvc, "payment_intent.succeeded", foreign)).hasStatusOk();
	}

	@Test
	void anEventThatFailsIsLoggedAndHandledWhenStripeSendsItAgain() {
		stripe.succeed(intentId);
		String payload = StripeWebhooks.event("payment_intent.succeeded", intentId);
		String eventId = JsonPath.read(payload, "$.id");
		stripe.failNextCall();

		assertThat(StripeWebhooks.send(mvc, payload)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(booking()).containsEntry("status", "PENDING");
		assertThat(eventRow(eventId)).containsEntry("processed", false);
		assertThat((String) eventRow(eventId).get("error")).contains("PaymentGatewayException");

		assertThat(StripeWebhooks.send(mvc, payload)).hasStatusOk();
		assertThat(booking()).containsEntry("status", "CONFIRMED");
		assertThat(eventRow(eventId)).containsEntry("processed", true).containsEntry("error", null);
	}

	@Test
	void eventsThisApiDoesntUseAreAcknowledged() {
		String payload = StripeWebhooks.event("charge.succeeded", "ch_" + uniqueSuffix());

		assertThat(StripeWebhooks.send(mvc, payload)).hasStatusOk();
		assertThat(eventRow(JsonPath.read(payload, "$.id"))).containsEntry("type", "charge.succeeded")
			.containsEntry("processed", true);
	}

	private Map<String, Object> booking() {
		return jdbc.sql("""
				select status, confirmed_at is not null as confirmed from bookings where reference = :reference""")
			.param("reference", reference)
			.query()
			.singleRow();
	}

	private Map<String, Object> payment() {
		return jdbc.sql("""
				select status, failure_reason, paid_at is not null as paid from payments
				where stripe_payment_intent_id = :id""")
			.param("id", intentId)
			.query()
			.singleRow();
	}

	private List<Tuple> history() {
		return jdbc.sql("""
				select h.from_status, h.to_status, h.changed_by, h.reason
				from booking_status_history h join bookings b on b.id = h.booking_id
				where b.reference = :reference order by h.id""")
			.param("reference", reference)
			.query()
			.listOfRows()
			.stream()
			.map(row -> tuple(row.get("from_status"), row.get("to_status"), row.get("changed_by"), row.get("reason")))
			.toList();
	}

	private Map<String, Object> eventRow(String eventId) {
		return jdbc.sql("""
				select type, processed_at is not null as processed, error from stripe_events where id = :id""")
			.param("id", eventId)
			.query()
			.listOfRows()
			.stream()
			.findFirst()
			.orElse(null);
	}

}
