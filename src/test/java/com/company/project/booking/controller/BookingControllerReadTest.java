package com.company.project.booking.controller;

import static com.company.project.booking.controller.BookingClient.booking;
import static com.company.project.booking.controller.BookingClient.total;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
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
 * The booking page (C-PAY-03): everything a confirmation shows, for the booking's own user only, the same as what
 * booking it answered.
 */
@IntegrationTest
class BookingControllerReadTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(50);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakePaymentGateway stripe;

	CatalogFixtures catalog;

	BookingClient client;

	CreatedHotel hotel;

	long ratePlan;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		hotel = catalog.hotel("Confirmed " + uniqueSuffix()).country("XP").create();
		ratePlan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 1, 3, 5));
		client = BookingClient.signUp(mvc, catalog);
	}

	@Test
	void aPaidBookingShowsItsHotelRoomsTermsPriceAndPayment() {
		// The only test writing a tax rule for XI, so the booking has exactly this one tax.
		hotel = catalog.hotel("Confirmed " + uniqueSuffix()).country("XI").create();
		jdbc.sql("update hotels set phone = '+84 236 3888 999', email = 'stay@example.com' where id = :id")
			.param("id", hotel.id())
			.update();
		catalog.taxRule("XI", "VAT", "PERCENT", "10", null, false, true);
		ratePlan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 1, 3, 5));
		long line = client.addToCart(ratePlan, CHECK_IN, CHECK_OUT, 1, 2, 6);
		MvcTestResult booked = client.book(booking(total(client.quote(null, line)), line)
			.guest("Minh Tran", "Late check-in", "21:00")
			.leadGuests("[{\"cartItemId\": %d, \"names\": [\"Lan Nguyen\"]}]".formatted(line)));
		assertThat(booked).hasStatus(HttpStatus.CREATED);
		String reference = JsonPath.read(body(booked), "$.reference");
		client.pay(reference, stripe);

		MvcTestResult found = client.get("/api/bookings/" + reference);

		assertThat(found).hasStatusOk();
		String json = body(found);
		assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("CONFIRMED");
		assertThat(Instant.parse(JsonPath.read(json, "$.confirmedAt"))).isBefore(Instant.now());
		assertThat(JsonPath.<Object>read(json, "$.holdExpiresAt")).isNull();
		assertThat(JsonPath.<String>read(json, "$.hotel.name")).isEqualTo(hotel.name());
		assertThat(JsonPath.<String>read(json, "$.hotel.address.formatted")).isNotBlank();
		assertThat(JsonPath.<String>read(json, "$.hotel.phone")).isEqualTo("+84 236 3888 999");
		assertThat(JsonPath.<String>read(json, "$.hotel.email")).isEqualTo("stay@example.com");
		assertThat(JsonPath.<String>read(json, "$.hotel.checkInFrom")).isNotNull();
		assertThat(JsonPath.<String>read(json, "$.guestName")).isEqualTo("Minh Tran");
		assertThat(JsonPath.<Integer>read(json, "$.nights")).isEqualTo(2);
		assertThat(JsonPath.<String>read(json, "$.rooms[0].leadGuestName")).isEqualTo("Lan Nguyen");
		assertThat(JsonPath.<List<Integer>>read(json, "$.rooms[0].childAges")).containsExactly(6);
		assertThat(JsonPath.<String>read(json, "$.rooms[0].ratePlanName")).isNotBlank();
		assertThat(JsonPath.<Boolean>read(json, "$.rooms[0].cancellation.refundable")).isNotNull();
		assertThat(JsonPath.<String>read(json, "$.payment.status")).isEqualTo("SUCCEEDED");
		assertThat(JsonPath.<Object>read(json, "$.cancellation")).isNull();
		// What the booking answered, apart from what changed since: its status and payment.
		String created = body(booked);
		assertThat(JsonPath.<Map<String, Object>>read(json, "$.price"))
			.isEqualTo(JsonPath.<Map<String, Object>>read(created, "$.price"));
		assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.price.taxes")).singleElement()
			.satisfies(tax -> assertThat(tax).containsEntry("name", "VAT"));
		assertThat(JsonPath.<List<Object>>read(json, "$.rooms[*].cancellation.freeUntil"))
			.isEqualTo(JsonPath.<List<Object>>read(created, "$.rooms[*].cancellation.freeUntil"));
		assertThat(JsonPath.<List<Object>>read(json, "$.rooms[*].ratePlanName"))
			.isEqualTo(JsonPath.<List<Object>>read(created, "$.rooms[*].ratePlanName"));
		assertThat(JsonPath.<Map<String, Object>>read(json, "$.booker"))
			.isEqualTo(JsonPath.<Map<String, Object>>read(created, "$.booker"));
	}

	@Test
	void aPendingBookingShowsItsHoldAndItsPaymentOnceStarted() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);

		String before = body(client.get("/api/bookings/" + reference));
		assertThat(JsonPath.<String>read(before, "$.status")).isEqualTo("PENDING");
		assertThat(JsonPath.<String>read(before, "$.holdExpiresAt")).isNotNull();
		assertThat(JsonPath.<Object>read(before, "$.payment")).isNull();

		client.startPayment(reference);

		assertThat(JsonPath.<String>read(body(client.get("/api/bookings/" + reference)), "$.payment.status"))
			.isEqualTo("REQUIRES_PAYMENT");
	}

	@Test
	void anotherUsersBookingIsNotFound() {
		String reference = client.bookRooms(ratePlan, CHECK_IN, CHECK_OUT, 1, null);

		assertThat(BookingClient.signUp(mvc, catalog).get("/api/bookings/" + reference))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(client.get("/api/bookings/HB23456789")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
