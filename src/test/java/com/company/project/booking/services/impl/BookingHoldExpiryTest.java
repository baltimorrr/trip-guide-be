package com.company.project.booking.services.impl;

import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.company.project.booking.dto.BookerDetails;
import com.company.project.booking.dto.BookingRequest;
import com.company.project.booking.services.BookingService;
import com.company.project.common.dto.Money;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A Pending booking whose hold ran out unpaid expires (C-BOOK-04): its rooms go back on sale, its code use no longer
 * counts, and the change is logged as the system's. A run expires every test's past holds, so only this test may
 * leave a hold in the past.
 */
@IntegrationTest
class BookingHoldExpiryTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(70);

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingService bookings;

	@Test
	void anUnpaidBookingExpiresAndGivesBackItsRoomsAndItsCode() {
		CatalogFixtures catalog = new CatalogFixtures(jdbc);
		String suffix = uniqueSuffix();
		long room = catalog.roomType(catalog.hotel("Expiring " + suffix).create().id(), 2, 0, 2, 5);
		long plan = catalog.defaultRatePlan(room);
		long codeId = catalog.discountCode("expiring-" + suffix).create();
		long late = catalog.user();
		long onTime = catalog.user();
		String expired = book(late, catalog.cartItem(late, plan, CHECK_IN, CHECK_IN.plusDays(2), 2, 2),
				"expiring-" + suffix, 3_600_000);
		String held = book(onTime, catalog.cartItem(onTime, plan, CHECK_IN, CHECK_IN.plusDays(2), 1, 2), null,
				2 * DEFAULT_PRICE);
		assertThat(bookedRooms(room)).containsExactly(3, 3);
		jdbc.sql("update bookings set hold_expires_at = now() - interval '1 minute' where reference = :reference")
			.param("reference", expired)
			.update();

		int count = 0;
		for (int batch = bookings.expireHolds(); batch > 0; batch = bookings.expireHolds()) {
			count += batch;
		}

		assertThat(count).isPositive();
		assertThat(status(expired)).isEqualTo("EXPIRED");
		assertThat(status(held)).isEqualTo("PENDING");
		assertThat(bookedRooms(room)).containsExactly(1, 1);
		assertThat(jdbc.sql("select count(*) from discount_redemptions where discount_code_id = :id")
			.param("id", codeId)
			.query(Long.class)
			.single()).isZero();
		assertThat(jdbc.sql("""
				select h.from_status, h.to_status, h.changed_by, h.reason
				from booking_status_history h join bookings b on b.id = h.booking_id
				where b.reference = :reference order by h.id""")
			.param("reference", expired)
			.query()
			.listOfRows()).extracting(row -> row.get("from_status"), row -> row.get("to_status"),
					row -> row.get("changed_by"), row -> row.get("reason"))
			.containsExactly(tuple(null, "PENDING", late, null),
					tuple("PENDING", "EXPIRED", null,
							"Payment not completed before the hold expired"));
	}

	private String book(long userId, long cartItemId, String code, long totalMinor) {
		return bookings.create(userId, new BookingRequest(List.of(cartItemId), code,
				new Money(BigDecimal.valueOf(totalMinor), "VND"),
				new BookerDetails("Lan Nguyen", "lan@example.com", "+84901234567", "VN"), null, null, null, null, true))
			.reference();
	}

	private String status(String reference) {
		return jdbc.sql("select status from bookings where reference = :reference")
			.param("reference", reference)
			.query(String.class)
			.single();
	}

	private List<Integer> bookedRooms(long roomTypeId) {
		return jdbc.sql("select booked_rooms from room_inventory where room_type_id = :id order by stay_date")
			.param("id", roomTypeId)
			.query(Integer.class)
			.list();
	}

}
