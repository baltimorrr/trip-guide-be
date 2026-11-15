package com.company.project.booking.repository;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The database's booking lifecycle (V10's triggers): only the lifecycle's arrows, each change logged. */
@IntegrationTest
class BookingStatusRulesTest {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate transactions;

	CatalogFixtures catalog;

	long hotelId;

	long userId;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		hotelId = catalog.hotel("Lifecycle " + uniqueSuffix()).create().id();
		userId = catalog.user();
	}

	@Test
	void aBookingStartsPendingAndItsUserIsLoggedAsTheCreator() {
		long bookingId = insert("PENDING");

		assertThat(history(bookingId)).containsExactly(new Change(null, "PENDING", userId, null));
		assertThatThrownBy(() -> insert("CONFIRMED")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void everyLifecycleArrowIsAllowedAndLogged() {
		long stayed = insert("PENDING");
		move(stayed, "CONFIRMED", "confirmed_at = now()");
		move(stayed, "CHECKED_IN", "checked_in_at = now()");
		move(stayed, "CHECKED_OUT", "checked_out_at = now()");
		assertThat(history(stayed)).extracting(Change::to)
			.containsExactly("PENDING", "CONFIRMED", "CHECKED_IN", "CHECKED_OUT");
		assertThat(history(stayed).get(3)).isEqualTo(new Change("CHECKED_IN", "CHECKED_OUT", null, null));

		long expired = insert("PENDING");
		move(expired, "EXPIRED", null);
		long cancelledBeforePaying = insert("PENDING");
		move(cancelledBeforePaying, "CANCELLED", "cancelled_at = now()");
		long cancelled = insert("PENDING");
		move(cancelled, "CONFIRMED", "confirmed_at = now()");
		move(cancelled, "CANCELLED", "cancelled_at = now()");
		long noShow = insert("PENDING");
		move(noShow, "CONFIRMED", "confirmed_at = now()");
		move(noShow, "NO_SHOW", null);
		assertThat(List.of(expired, cancelledBeforePaying, cancelled, noShow)).map(this::status)
			.containsExactly("EXPIRED", "CANCELLED", "CANCELLED", "NO_SHOW");
	}

	@Test
	void anyOtherChangeIsRefused() {
		long pending = insert("PENDING");
		assertThatThrownBy(() -> move(pending, "CHECKED_IN", "confirmed_at = now(), checked_in_at = now()"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("can't go from PENDING to CHECKED_IN");

		long checkedIn = insert("PENDING");
		move(checkedIn, "CONFIRMED", "confirmed_at = now()");
		move(checkedIn, "CHECKED_IN", "checked_in_at = now()");
		assertThatThrownBy(() -> move(checkedIn, "CANCELLED", "cancelled_at = now()"))
			.isInstanceOf(DataIntegrityViolationException.class);

		long expired = insert("PENDING");
		move(expired, "EXPIRED", null);
		assertThatThrownBy(() -> move(expired, "CONFIRMED", "confirmed_at = now()"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(List.of(pending, checkedIn, expired)).map(this::status)
			.containsExactly("PENDING", "CHECKED_IN", "EXPIRED");
	}

	@Test
	void anUpdateThatKeepsTheStatusLogsNothing() {
		long bookingId = insert("PENDING");

		jdbc.sql("update bookings set status = status, guest_name = 'Someone else' where id = :id")
			.param("id", bookingId)
			.update();

		assertThat(history(bookingId)).hasSize(1);
	}

	@Test
	void whoAndWhyComeFromTheTransactionsSettings() {
		long bookingId = insert("PENDING");
		long admin = catalog.user();

		transactions.executeWithoutResult(status -> {
			jdbc.sql("select set_config('app.changed_by', :admin, true), set_config('app.status_reason', 'Overbooked', true)")
				.param("admin", String.valueOf(admin))
				.query()
				.singleRow();
			move(bookingId, "CANCELLED", "cancelled_at = now()");
		});
		// The settings end with their transaction.
		long next = insert("PENDING");

		assertThat(history(bookingId).getLast()).isEqualTo(new Change("PENDING", "CANCELLED", admin, "Overbooked"));
		assertThat(history(next)).containsExactly(new Change(null, "PENDING", userId, null));
	}

	private long insert(String status) {
		return jdbc.sql("""
				insert into bookings (reference, user_id, hotel_id, check_in, check_out, status, booker_name,
				                      booker_email, booker_phone, terms_accepted_at, currency, subtotal_minor,
				                      total_minor, commission_rate, hold_expires_at, confirmed_at, created_at,
				                      updated_at)
				values ('HB' || upper(:suffix), :userId, :hotelId, current_date + 30, current_date + 32, :status,
				        'Booker', 'booker@test.local', '+84901234567', now(), 'VND', 100, 100, 15,
				        now() + interval '15 minutes', now(), now(), now())
				returning id""")
			.param("suffix", uniqueSuffix())
			.param("userId", userId)
			.param("hotelId", hotelId)
			.param("status", status)
			.query(Long.class)
			.single();
	}

	private void move(long bookingId, String status, String timestamps) {
		jdbc.sql("update bookings set status = :status" + (timestamps == null ? "" : ", " + timestamps)
				+ " where id = :id")
			.param("status", status)
			.param("id", bookingId)
			.update();
	}

	private String status(long bookingId) {
		return jdbc.sql("select status from bookings where id = :id").param("id", bookingId).query(String.class).single();
	}

	private List<Change> history(long bookingId) {
		return jdbc.sql("""
				select from_status, to_status, changed_by, reason from booking_status_history
				where booking_id = :id order by id""")
			.param("id", bookingId)
			.query((rs, row) -> new Change(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class),
					rs.getString(4)))
			.list();
	}

	record Change(String from, String to, Long changedBy, String reason) {
	}

}
