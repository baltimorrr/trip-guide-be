package com.company.project.booking.repository;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.company.project.booking.model.Booker;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingNight;
import com.company.project.booking.model.BookingRoom;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.BookingTax;
import com.company.project.booking.model.CancellationTerms;
import com.company.project.cart.model.RoomGuests;
import com.company.project.rate.model.PenaltyType;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The V10 booking mappings: a booking saved with its rooms, nights and taxes reads back as it was written. */
@IntegrationTest
class BookingMappingTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(40);

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingRepository bookings;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void aBookingReadsBackWithItsRoomsNightsTermsAndTaxes() {
		CatalogFixtures catalog = new CatalogFixtures(jdbc);
		long hotelId = catalog.hotel("Mapped " + uniqueSuffix()).create().id();
		long roomTypeId = catalog.roomType(hotelId, 2, 2, 4, 5);
		long ratePlanId = catalog.defaultRatePlan(roomTypeId);
		long userId = catalog.user();
		CancellationTerms terms = new CancellationTerms("Free until 48h before", true,
				List.of(new CancellationTerms.Rule(48, PenaltyType.NIGHTS, new BigDecimal("1.00"))));
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		Booking booking = Booking.create("HB" + uniqueSuffix().toUpperCase(), userId, hotelId, CHECK_IN,
				CHECK_IN.plusDays(2), new Booker(" Lan Nguyen ", "Lan@Example.com", "+84901234567", "vn"), "Minh Tran",
				" Late check-in ", LocalTime.of(18, 30), now, now.plus(15, ChronoUnit.MINUTES), "VND",
				new BigDecimal("15.00"));
		List<BookingNight> nights = List.of(new BookingNight(CHECK_IN, 1_000_000),
				new BookingNight(CHECK_IN.plusDays(1), 1_200_000));
		booking.addRoom(BookingRoom.create(roomTypeId, ratePlanId, "Deluxe Double", "BB", terms,
				new RoomGuests(2, List.of(9, 4)), "Lan Nguyen", nights));
		booking.addRoom(BookingRoom.create(roomTypeId, ratePlanId, "Deluxe Double", "BB", terms,
				new RoomGuests(1, List.of()), null, nights));
		booking.applyDiscount(catalog.discountCode("map" + uniqueSuffix()).create(), 440_000);
		booking.addTax(BookingTax.create(null, "VAT", false, 396_000));
		booking.addTax(BookingTax.create(null, "City fee", true, 20_000));
		long bookingId = bookings.saveAndFlush(booking).getId();

		transactions.executeWithoutResult(status -> {
			Booking read = bookings.findById(bookingId).orElseThrow();
			assertThat(read.getStatus()).isEqualTo(BookingStatus.PENDING);
			assertThat(read.getBookerName()).isEqualTo("Lan Nguyen");
			assertThat(read.getBookerEmail()).isEqualTo("lan@example.com");
			assertThat(read.getBookerCountry()).isEqualTo("VN");
			assertThat(read.getSpecialRequests()).isEqualTo("Late check-in");
			assertThat(read.getEstimatedArrival()).isEqualTo(LocalTime.of(18, 30));
			assertThat(read.getHoldExpiresAt()).isEqualTo(now.plus(15, ChronoUnit.MINUTES));
			assertThat(read.getSubtotalMinor()).isEqualTo(4_400_000);
			assertThat(read.getTaxMinor()).isEqualTo(396_000);
			assertThat(read.getTotalMinor()).isEqualTo(4_400_000 - 440_000 + 396_000);
			assertThat(read.getRooms()).hasSize(2);
			BookingRoom first = read.getRooms().getFirst();
			assertThat(first.getCancellationPolicy()).isEqualTo(terms);
			assertThat(first.guests()).isEqualTo(new RoomGuests(2, List.of(4, 9)));
			assertThat(first.getNights()).extracting(BookingNight::getStayDate, BookingNight::getPriceMinor)
				.containsExactly(tuple(CHECK_IN, 1_000_000L),
						tuple(CHECK_IN.plusDays(1), 1_200_000L));
			assertThat(first.getTotalMinor()).isEqualTo(2_200_000);
			assertThat(read.getRooms().get(1).getLeadGuestName()).isNull();
			assertThat(read.getTaxes()).extracting(BookingTax::getName, BookingTax::isIncluded)
				.containsExactly(tuple("VAT", false),
						tuple("City fee", true));
		});
		assertThat(jdbc.sql("select nights from bookings where id = :id").param("id", bookingId).query(Integer.class)
			.single()).isEqualTo(2);
	}

}
