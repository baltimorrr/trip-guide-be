package com.company.project.booking.notification;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.company.project.booking.controller.BookingClient;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.FakePaymentGateway;
import com.company.project.support.IntegrationTest;
import com.company.project.support.RecordingBookingMailService;
import com.company.project.support.RecordingBookingMailService.Mail;
import com.company.project.support.StripeWebhooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The confirmation emails (C-PAY-03): once the payment confirms a booking, the customer gets a confirmation and the
 * hotel's partner a new-booking notice, each with the whole booking; never twice, and never failing the payment.
 */
@IntegrationTest
class BookingMailListenerTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(55);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakePaymentGateway stripe;

	CatalogFixtures catalog;

	CreatedHotel hotel;

	long ratePlan;

	BookingClient client;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		hotel = catalog.hotel("Mailed " + uniqueSuffix()).country("XP").create();
		jdbc.sql("update hotels set phone = '+84 28 1234 5678', email = 'front@example.com' where id = :id")
			.param("id", hotel.id())
			.update();
		ratePlan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 0, 2, 5));
		client = BookingClient.signUp(mvc, catalog);
	}

	@Test
	void aPaidBookingSendsTheCustomerAndTheHotelOneEmailEach() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_IN.plusDays(3), 2, null);
		assertThat(RecordingBookingMailService.mails(reference)).isEmpty();

		client.pay(reference, stripe);

		Mail confirmation = single(reference, "confirmation");
		// The booker of BookingClient's booking requests.
		assertThat(confirmation.to()).isEqualTo("lan@example.com");
		BookingResponse booking = confirmation.booking();
		assertThat(booking.reference()).isEqualTo(reference);
		assertThat(booking.status().name()).isEqualTo("CONFIRMED");
		assertThat(booking.hotel().name()).isEqualTo(hotel.name());
		assertThat(booking.hotel().address().formatted()).isNotBlank();
		assertThat(booking.hotel().phone()).isEqualTo("+84 28 1234 5678");
		assertThat(booking.hotel().email()).isEqualTo("front@example.com");
		assertThat(booking.checkIn()).isEqualTo(CHECK_IN);
		assertThat(booking.checkOut()).isEqualTo(CHECK_IN.plusDays(3));
		assertThat(booking.rooms()).hasSize(2).allSatisfy(room -> {
			assertThat(room.adults()).isEqualTo(2);
			assertThat(room.boardType()).isNotBlank();
			assertThat(room.cancellation()).isNotNull();
		});
		assertThat(booking.price().total()).isNotNull();
		Mail notice = single(reference, "new-booking");
		assertThat(notice.to()).isEqualTo(partnerEmail());
		assertThat(notice.booking()).isEqualTo(booking);
	}

	@Test
	void theSameConfirmationDeliveredTwiceSendsTheEmailsOnce() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_IN.plusDays(1), 1, null);
		String intentId = client.pay(reference, stripe);

		StripeWebhooks.send(mvc, "payment_intent.succeeded", intentId);

		assertThat(RecordingBookingMailService.mails(reference, "confirmation")).hasSize(1);
		assertThat(RecordingBookingMailService.mails(reference, "new-booking")).hasSize(1);
	}

	@Test
	void aBookingWithNothingToPaySendsTheEmailsToo() {
		String code = "gift-" + uniqueSuffix();
		catalog.discountCode(code).percent("100", null, null).create();
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_IN.plusDays(1), 1, code);

		assertThat(client.startPayment(reference)).hasStatusOk();

		assertThat(single(reference, "confirmation").booking().price().discount().code()).isEqualTo(code);
		single(reference, "new-booking");
	}

	@Test
	void anEmailThatFailsNeverFailsThePayment() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_IN.plusDays(1), 1, null);
		RecordingBookingMailService.failNext();

		client.pay(reference, stripe);

		assertThat(jdbc.sql("select status from bookings where reference = :reference")
			.param("reference", reference)
			.query(String.class)
			.single()).isEqualTo("CONFIRMED");
		assertThat(RecordingBookingMailService.mails(reference, "confirmation")).isEmpty();
		// The hotel's notice is a separate email and still goes out.
		single(reference, "new-booking");
	}

	private static Mail single(String reference, String kind) {
		assertThat(RecordingBookingMailService.mails(reference, kind)).hasSize(1);
		return RecordingBookingMailService.mails(reference, kind).getFirst();
	}

	private String partnerEmail() {
		return jdbc.sql("select p.contact_email from hotels h join partners p on p.id = h.partner_id where h.id = :id")
			.param("id", hotel.id())
			.query(String.class)
			.single();
	}

}
