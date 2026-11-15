package com.company.project.booking.services.impl;

import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.company.project.booking.dto.BookerDetails;
import com.company.project.booking.dto.BookingRequest;
import com.company.project.booking.services.BookingService;
import com.company.project.common.dto.Money;
import com.company.project.common.exception.BusinessException;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Two customers book a room type's last room at the same moment (C-BOOK-04): one gets it, the other a 409, and the
 * room is never booked twice. It goes through the service, so each customer has a transaction of their own.
 */
@IntegrationTest
class BookingConcurrencyTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(60);

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingService bookings;

	@RepeatedTest(5)
	void onlyOneOfTwoSimultaneousBookingsGetsTheLastRoom() throws Exception {
		CatalogFixtures catalog = new CatalogFixtures(jdbc);
		long room = catalog.roomType(catalog.hotel("Last room " + uniqueSuffix()).create().id(), 2, 0, 2, 1);
		long plan = catalog.defaultRatePlan(room);
		List<Long> users = List.of(catalog.user(), catalog.user());
		List<Long> lines = users.stream()
			.map(user -> catalog.cartItem(user, plan, CHECK_IN, CHECK_IN.plusDays(2), 1, 2))
			.toList();
		CountDownLatch start = new CountDownLatch(1);

		ExecutorService threads = Executors.newFixedThreadPool(2);
		List<Future<String>> outcomes;
		try {
			outcomes = List.of(0, 1).stream().map(i -> threads.submit(() -> {
				start.await();
				try {
					return bookings.create(users.get(i), request(lines.get(i))).reference();
				}
				catch (BusinessException ex) {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					return "refused";
				}
			})).toList();
			start.countDown();
		}
		finally {
			threads.shutdown();
		}

		List<String> results = outcomes.stream().map(outcome -> {
			try {
				return outcome.get();
			}
			catch (Exception ex) {
				throw new AssertionError(ex);
			}
		}).toList();
		assertThat(results).containsOnlyOnce("refused").anyMatch(result -> result.startsWith("HB"));
		assertThat(jdbc.sql("select booked_rooms from room_inventory where room_type_id = :id order by stay_date")
			.param("id", room)
			.query(Integer.class)
			.list()).containsExactly(1, 1);
		assertThat(jdbc.sql("""
				select count(*) from bookings b join booking_rooms r on r.booking_id = b.id where r.room_type_id = :id""")
			.param("id", room)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	private static BookingRequest request(long cartItemId) {
		return new BookingRequest(List.of(cartItemId), null,
				new Money(BigDecimal.valueOf(2 * DEFAULT_PRICE), "VND"),
				new BookerDetails("Lan Nguyen", "lan@example.com", "+84901234567", "VN"), null, null, null, null, true);
	}

}
