package com.company.project.review.repository;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.project.review.model.Review;
import com.company.project.review.model.ReviewStatus;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The guards on reviews (V8, and V10's link to bookings) and the derived query the hotel page reads. */
@IntegrationTest
class ReviewRepositoryTest {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ReviewRepository reviews;

	CatalogFixtures catalog;

	CreatedHotel hotel;

	long reviewer;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		hotel = catalog.hotel("Guarded " + uniqueSuffix()).create();
		reviewer = catalog.user();
	}

	@Test
	void oneReviewPerBooking() {
		long booking = anyBooking();
		insert(booking, 8, null, "PUBLISHED");

		assertThatThrownBy(() -> insert(booking, 9, null, "PUBLISHED"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aReviewIsOfAnExistingBooking() {
		assertThatThrownBy(() -> insert(Long.MAX_VALUE, 8, null, "PUBLISHED"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void scoresRunFromOneToTen() {
		assertThatThrownBy(() -> insert(anyBooking(), 0, null, "PUBLISHED"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(anyBooking(), 11, null, "PUBLISHED"))
			.isInstanceOf(DataIntegrityViolationException.class);
		insert(anyBooking(), 1, null, "PUBLISHED");
		insert(anyBooking(), 10, null, "PUBLISHED");
	}

	@Test
	void travellerTypeAndStatusHoldTheEnumNames() {
		assertThatThrownBy(() -> insert(anyBooking(), 8, "ALONE", "PUBLISHED"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insert(anyBooking(), 8, null, "DRAFT"))
			.isInstanceOf(DataIntegrityViolationException.class);
		insert(anyBooking(), 8, "BUSINESS", "HIDDEN");
	}

	@Test
	void findsOnlyThatHotelsReviewsWithTheStatus() {
		long published = catalog.review(hotel.id(), reviewer, 8).create();
		catalog.review(hotel.id(), reviewer, 2).hidden().create();
		CreatedHotel other = catalog.hotel("Other " + uniqueSuffix()).create();
		catalog.review(other.id(), reviewer, 9).create();

		assertThat(reviews.findByHotelIdAndStatus(hotel.id(), ReviewStatus.PUBLISHED, PageRequest.of(0, 10)))
			.extracting(Review::getId)
			.containsExactly(published);
	}

	private void insert(long bookingId, int score, String travellerType, String status) {
		jdbc.sql("""
				insert into reviews (booking_id, hotel_id, user_id, score, traveller_type, status, created_at, updated_at)
				values (:bookingId, :hotelId, :userId, :score, :travellerType, :status, now(), now())""")
			.param("bookingId", bookingId)
			.param("hotelId", hotel.id())
			.param("userId", reviewer)
			.param("score", score)
			.param("travellerType", travellerType)
			.param("status", status)
			.update();
	}

	private long anyBooking() {
		return catalog.booking(reviewer, hotel.id());
	}

}
