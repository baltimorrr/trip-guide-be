package com.company.project.booking.repository;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Holding rooms in {@code room_inventory}: only where they fit, for every night, creating missing nights. */
@IntegrationTest
class RoomInventoryRepositoryTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(50);

	@Autowired
	JdbcClient jdbc;

	@Autowired
	RoomInventoryRepository inventory;

	@Autowired
	TransactionTemplate transactions;

	CatalogFixtures catalog;

	long hotelId;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		hotelId = catalog.hotel("Held " + uniqueSuffix()).create().id();
	}

	@Test
	void aHoldCreatesTheMissingNightsFromTheRoomTypesTotal() {
		long room = catalog.roomType(hotelId, 2, 2, 4, 5);

		assertThat(hold(room, 2, CHECK_IN, CHECK_IN.plusDays(2), false)).isTrue();

		assertThat(nights(room)).containsExactly(new Night(CHECK_IN, 5, 2), new Night(CHECK_IN.plusDays(1), 5, 2));
	}

	@Test
	void aHoldAddsToTheNightsThatExistUntilTheyAreFull() {
		long room = catalog.roomType(hotelId, 2, 2, 4, 5);
		catalog.inventory(room, CHECK_IN.plusDays(1), 3, 1, false);

		assertThat(hold(room, 2, CHECK_IN, CHECK_IN.plusDays(2), false)).isTrue();
		assertThat(hold(room, 1, CHECK_IN, CHECK_IN.plusDays(2), true)).isFalse();

		assertThat(nights(room)).containsExactly(new Night(CHECK_IN, 5, 2), new Night(CHECK_IN.plusDays(1), 3, 3));
	}

	@Test
	void aClosedNightOrMoreRoomsThanTheTypeHasHoldsNothing() {
		long closed = catalog.roomType(hotelId, 2, 2, 4, 5);
		catalog.inventory(closed, CHECK_IN, 5, 0, true);
		long small = catalog.roomType(hotelId, 2, 2, 4, 2);

		assertThat(hold(closed, 1, CHECK_IN, CHECK_IN.plusDays(2), true)).isFalse();
		assertThat(hold(small, 3, CHECK_IN, CHECK_IN.plusDays(2), true)).isFalse();

		assertThat(nights(closed)).containsExactly(new Night(CHECK_IN, 5, 0));
		assertThat(nights(small)).isEmpty();
	}

	/** Holds in a transaction of its own, rolled back when {@code rollBack}, as the service does when it fails. */
	private boolean hold(long roomTypeId, int rooms, LocalDate checkIn, LocalDate checkOut, boolean rollBack) {
		return transactions.execute(status -> {
			boolean held = inventory.hold(roomTypeId, rooms, checkIn, checkOut);
			if (rollBack) {
				status.setRollbackOnly();
			}
			return held;
		});
	}

	private List<Night> nights(long roomTypeId) {
		return jdbc.sql("""
				select stay_date, total_rooms, booked_rooms from room_inventory where room_type_id = :id
				order by stay_date""")
			.param("id", roomTypeId)
			.query((rs, row) -> new Night(rs.getObject(1, LocalDate.class), rs.getInt(2), rs.getInt(3)))
			.list();
	}

	record Night(LocalDate date, int total, int booked) {
	}

}
