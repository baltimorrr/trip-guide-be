package com.company.project.booking.controller;

import static com.company.project.booking.controller.BookingClient.booking;
import static com.company.project.booking.controller.BookingClient.money;
import static com.company.project.booking.controller.BookingClient.total;
import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Booking a quoted cart group (C-BOOK-01/04/05/06): a Pending booking that holds its rooms, with the guest details,
 * one row per room and the breakdown; a changed total must be confirmed first; the lines leave the cart.
 */
@IntegrationTest
class BookingControllerTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	BookingClient client;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		client = BookingClient.signUp(mvc, catalog);
	}

	@Test
	void aBookingHoldsItsRoomsWithTheGuestDetailsAndTheQuotedTotal() {
		CreatedHotel hotel = catalog.hotel("Booked " + suffix).country("XG").create();
		catalog.taxRule("XG", "VAT", "PERCENT", "10", null, false, true);
		long standardRoom = catalog.roomType(hotel.id(), 2, 2, 4, 5);
		long standard = catalog.defaultRatePlan(standardRoom);
		long deluxe = catalog.ratePlan(catalog.bareRoomType(hotel.id(), 2, 1, 3, 3)).board("BB").price(1_500_000)
			.create();
		long standardLine = client.addToCart(standard, CHECK_IN, CHECK_OUT, 2, 2);
		long deluxeLine = client.addToCart(deluxe, CHECK_IN, CHECK_OUT, 1, 2, 7);
		long laterLine = client.addToCart(standard, CHECK_OUT, CHECK_OUT.plusDays(1), 1, 2);
		String total = total(client.quote(null, standardLine, deluxeLine));

		MvcTestResult booked = client.book(booking(total, standardLine, deluxeLine)
			.guest("Minh Tran", " High floor ", "18:30")
			.leadGuests("[{\"cartItemId\": %d, \"names\": [\"Lan Nguyen\", \"Hoa Le\"]}]".formatted(standardLine)));

		assertThat(booked).hasStatus(HttpStatus.CREATED);
		String json = body(booked);
		String reference = JsonPath.read(json, "$.reference");
		assertThat(reference).matches("HB[2-9A-HJKMNP-Z]{8}");
		assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("PENDING");
		assertThat(Instant.parse(JsonPath.read(json, "$.holdExpiresAt")))
			.isBetween(Instant.now().plus(14, ChronoUnit.MINUTES), Instant.now().plus(15, ChronoUnit.MINUTES));
		assertThat(JsonPath.<Map<String, Object>>read(json, "$.booker")).containsEntry("email", "lan@example.com")
			.containsEntry("countryCode", "VN");
		assertThat(JsonPath.<String>read(json, "$.specialRequests")).isEqualTo("High floor");
		assertThat(JsonPath.<String>read(json, "$.estimatedArrival")).isEqualTo("18:30:00");
		assertThat(JsonPath.<List<String>>read(json, "$.rooms[*].leadGuestName")).containsExactly("Lan Nguyen", "Hoa Le",
				null);
		assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.rooms[*].price")).extracting(BookingClient::money)
			.containsExactly(2 * DEFAULT_PRICE, 2 * DEFAULT_PRICE, 3_000_000L);
		assertThat(JsonPath.<List<Integer>>read(json, "$.rooms[2].childAges")).containsExactly(7);
		assertThat(money(JsonPath.read(json, "$.price.total"))).isEqualTo(7_700_000);

		Map<String, Object> row = jdbc.sql("""
				select b.id, b.status, b.guest_name, b.special_requests, cast(b.estimated_arrival as text) as arrival,
				       b.terms_accepted_at is not null as accepted, b.subtotal_minor, b.tax_minor, b.total_minor,
				       b.commission_rate,
				       (select count(*) from booking_rooms r where r.booking_id = b.id) as rooms,
				       (select count(*) from booking_room_nights n join booking_rooms r on r.id = n.booking_room_id
				        where r.booking_id = b.id) as nights,
				       (select count(*) from booking_taxes t where t.booking_id = b.id) as taxes,
				       (select h.changed_by from booking_status_history h where h.booking_id = b.id) as created_by
				from bookings b where b.reference = :reference""")
			.param("reference", reference)
			.query()
			.singleRow();
		assertThat(row).containsEntry("status", "PENDING")
			.containsEntry("guest_name", "Minh Tran")
			.containsEntry("special_requests", "High floor")
			.containsEntry("arrival", "18:30:00")
			.containsEntry("accepted", true)
			.containsEntry("subtotal_minor", 7_000_000L)
			.containsEntry("tax_minor", 700_000L)
			.containsEntry("total_minor", 7_700_000L)
			.containsEntry("rooms", 3L)
			.containsEntry("nights", 6L)
			.containsEntry("taxes", 1L)
			.containsEntry("created_by", client.userId);
		assertThat(row.get("commission_rate").toString()).isEqualTo("15.00");

		// The booked lines left the cart; the other group stayed.
		assertThat(JsonPath.<List<Integer>>read(body(client.cart()), "$.groups[*].items[*].id"))
			.containsExactly((int) laterLine);
		// The held rooms are no longer offered.
		assertThat(roomsLeft(hotel, standardRoom)).isEqualTo(3);
	}

	@Test
	void aChangedTotalMustBeConfirmedBeforeBooking() {
		CreatedHotel hotel = catalog.hotel("Repriced " + suffix).create();
		long room = catalog.roomType(hotel.id(), 2, 2, 4, 5);
		long plan = catalog.defaultRatePlan(room);
		long line = client.addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);
		String oldTotal = total(client.quote(null, line));
		catalog.nightPrice(plan, CHECK_IN, 1_400_000, null, false);

		MvcTestResult refused = client.book(booking(oldTotal, line));

		assertThat(refused).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(refused), "$.detail"))
			.isEqualTo("The total is now 2400000 VND. Check the new price and confirm it");
		assertThat(bookingsOf(client.userId)).isZero();
		assertThat(jdbc.sql("select count(*) from room_inventory where room_type_id = :id").param("id", room)
			.query(Long.class).single()).isZero();
		assertThat(client.cart()).bodyJson().extractingPath("$.groups[0].items[0].id").isEqualTo((int) line);

		MvcTestResult confirmed = client.book(booking(total(client.quote(null, line)), line));

		assertThat(confirmed).hasStatus(HttpStatus.CREATED);
		assertThat(money(JsonPath.read(body(confirmed), "$.price.total"))).isEqualTo(2_400_000);
	}

	@Test
	void theBookerMustGiveFullDetailsAndAcceptTheTerms() {
		long line = line("Details");
		String total = total(client.quote(null, line));

		assertThat(client.book(booking(total, line).acceptedTerms("false"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.book(booking(total, line).acceptedTerms(null))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.book(booking(total, line).booker("""
				{"fullName": "Lan Nguyen", "email": "lan@example.com", "countryCode": "VN"}""")))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.book(booking(total, line).booker("""
				{"fullName": "Lan Nguyen", "email": "lan@example.com", "phone": "+84901234567", "countryCode": "ZZZ"}""")))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.book(booking("{\"amount\": null, \"currency\": \"VND\"}", line)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(bookingsOf(client.userId)).isZero();
	}

	@Test
	void leadGuestsMustMatchTheBookedLines() {
		long line = line("Lead guests");
		long other = client.addToCart(catalog.defaultRatePlan(catalog.roomType(
				catalog.hotel("Elsewhere " + suffix).create().id(), 2, 2, 4, 5)), CHECK_IN, CHECK_OUT, 1, 2);
		String total = total(client.quote(null, line));

		MvcTestResult tooMany = client.book(booking(total, line)
			.leadGuests("[{\"cartItemId\": %d, \"names\": [\"Lan\", \"Hoa\"]}]".formatted(line)));
		MvcTestResult notBooked = client.book(booking(total, line)
			.leadGuests("[{\"cartItemId\": %d, \"names\": [\"Lan\"]}]".formatted(other)));

		assertThat(tooMany).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(body(tooMany), "$.detail"))
			.isEqualTo("Cart item %d has 1 rooms, so it takes at most 1 lead guests".formatted(line));
		assertThat(notBooked).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(bookingsOf(client.userId)).isZero();
	}

	@Test
	void aCodeIsUsedOnceTheBookingIsMade() {
		CreatedHotel hotel = catalog.hotel("Code used " + suffix).create();
		long plan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5));
		long codeId = catalog.discountCode("single-" + suffix).limits(1, null).create();
		long line = client.addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);
		BookingClient next = BookingClient.signUp(mvc, catalog);
		long nextLine = next.addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);
		assertThat(next.quote("single-" + suffix, nextLine)).hasStatusOk();

		MvcTestResult booked = client.book(booking(total(client.quote("single-" + suffix, line)), line)
			.code("single-" + suffix));

		assertThat(booked).hasStatus(HttpStatus.CREATED);
		assertThat(money(JsonPath.read(body(booked), "$.price.discount.amount"))).isEqualTo(200_000);
		assertThat(jdbc.sql("""
				select r.amount_minor from discount_redemptions r join bookings b on b.id = r.booking_id
				where r.discount_code_id = :codeId and b.reference = :reference""")
			.param("codeId", codeId)
			.param("reference", JsonPath.<String>read(body(booked), "$.reference"))
			.query(Long.class)
			.single()).isEqualTo(200_000);
		MvcTestResult usedUp = next.quote("single-" + suffix, nextLine);
		assertThat(usedUp).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(body(usedUp), "$.detail")).isEqualTo("This discount code has been fully used");
	}

	@Test
	void theSameLinesCanBeBookedOnlyOnce() {
		long line = line("Twice");
		String total = total(client.quote(null, line));

		assertThat(client.book(booking(total, line))).hasStatus(HttpStatus.CREATED);
		assertThat(client.book(booking(total, line))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bookingsOf(client.userId)).isEqualTo(1);
	}

	@Test
	void bookingNeedsASignedInUser() {
		assertThat(mvc.post().uri("/api/bookings").contentType(MediaType.APPLICATION_JSON)
			.content(booking("{\"amount\": 1, \"currency\": \"VND\"}", 1).json())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	/** A cart line of one room for two nights at a new hotel. */
	private long line(String name) {
		return client.addToCart(
				catalog.defaultRatePlan(catalog.roomType(catalog.hotel(name + " " + suffix).create().id(), 2, 2, 4, 5)),
				CHECK_IN, CHECK_OUT, 1, 2);
	}

	private long bookingsOf(long userId) {
		return jdbc.sql("select count(*) from bookings where user_id = :userId").param("userId", userId)
			.query(Long.class).single();
	}

	private int roomsLeft(CreatedHotel hotel, long roomTypeId) {
		MvcTestResult page = mvc.get()
			.uri("/api/hotels/%s/rooms?checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(hotel.slug(), CHECK_IN,
					CHECK_OUT))
			.exchange();
		assertThat(page).hasStatusOk();
		List<Integer> left = JsonPath.read(body(page), "$.roomTypes[?(@.roomTypeId == %d)].roomsLeft"
			.formatted(roomTypeId));
		return left.getFirst();
	}

}
