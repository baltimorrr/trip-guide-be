package com.company.project.payment.services.impl;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.company.project.booking.controller.BookingClient;
import com.company.project.booking.services.BookingService;
import com.company.project.payment.services.PaymentService;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.FakePaymentGateway;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The end of a hold with a payment started (C-PAY-02): the PaymentIntent is canceled on Stripe first, and only then
 * does the booking expire and give its rooms back. Like {@code BookingHoldExpiryTest}, these calls act on every test's
 * past holds; the assertions look only at this test's booking.
 */
@IntegrationTest
class PaymentHoldExpiryTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(75);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakePaymentGateway stripe;

	@Autowired
	PaymentService payments;

	@Autowired
	BookingService bookings;

	long room;

	String reference;

	String intentId;

	@BeforeEach
	void setUp() {
		CatalogFixtures catalog = new CatalogFixtures(jdbc);
		room = catalog.roomType(catalog.hotel("Hold end " + uniqueSuffix()).country("XP").create().id(), 2, 0, 2, 5);
		BookingClient client = BookingClient.signUp(mvc, catalog);
		reference = client.bookRooms(catalog.defaultRatePlan(room), CHECK_IN, CHECK_IN.plusDays(2), 2, null);
		assertThat(client.startPayment(reference)).hasStatusOk();
		intentId = stripe.intentIdOf(reference);
		assertThat(bookedRooms()).containsExactly(2, 2);
	}

	@Test
	void anOpenPaymentIsCanceledBeforeTheBookingExpiresAndGivesBackItsRooms() {
		endHold("1 minute");

		expireAll();
		assertThat(status()).isEqualTo("PENDING");
		assertThat(bookedRooms()).containsExactly(2, 2);

		assertThat(payments.cancelForExpiredHolds()).isPositive();
		assertThat(stripe.intent(intentId).status().name()).isEqualTo("CANCELED");
		assertThat(paymentStatus()).isEqualTo("CANCELED");

		expireAll();
		assertThat(status()).isEqualTo("EXPIRED");
		assertThat(bookedRooms()).containsExactly(0, 0);
	}

	@Test
	void anIntentThatAlreadySucceededConfirmsTheBookingInstead() {
		stripe.succeed(intentId);
		endHold("1 minute");

		payments.cancelForExpiredHolds();
		expireAll();

		assertThat(status()).isEqualTo("CONFIRMED");
		assertThat(paymentStatus()).isEqualTo("SUCCEEDED");
		assertThat(bookedRooms()).containsExactly(2, 2);
	}

	@Test
	void whileStripeCantCancelTheRoomsStayHeld() {
		// Older than any other test's hold, so it is the first payment the call tries.
		endHold("1 day");
		stripe.failNextCall();

		payments.cancelForExpiredHolds();
		expireAll();

		assertThat(status()).isEqualTo("PENDING");
		assertThat(paymentStatus()).isEqualTo("REQUIRES_PAYMENT");
		assertThat(bookedRooms()).containsExactly(2, 2);

		payments.cancelForExpiredHolds();
		expireAll();

		assertThat(status()).isEqualTo("EXPIRED");
		assertThat(bookedRooms()).containsExactly(0, 0);
	}

	private void endHold(String ago) {
		jdbc.sql("update bookings set hold_expires_at = now() - cast(:ago as interval) where reference = :reference")
			.param("ago", ago)
			.param("reference", reference)
			.update();
	}

	private void expireAll() {
		while (bookings.expireHolds() > 0) {
			// every batch until none is left
		}
	}

	private String status() {
		return jdbc.sql("select status from bookings where reference = :reference")
			.param("reference", reference)
			.query(String.class)
			.single();
	}

	private String paymentStatus() {
		return jdbc.sql("select status from payments where stripe_payment_intent_id = :id")
			.param("id", intentId)
			.query(String.class)
			.single();
	}

	private List<Integer> bookedRooms() {
		return jdbc.sql("select booked_rooms from room_inventory where room_type_id = :id order by stay_date")
			.param("id", room)
			.query(Integer.class)
			.list();
	}

}
