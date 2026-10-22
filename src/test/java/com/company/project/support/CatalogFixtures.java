package com.company.project.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Inserts hotel-catalog rows for integration tests. There are no hotel write APIs yet, so tests
 * build their data in SQL. The database is shared and never rolled back: give every place and hotel
 * a {@link #uniqueSuffix()} and assert only on your own rows.
 */
public final class CatalogFixtures {

	private final JdbcClient jdbc;

	public CatalogFixtures(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 8 lowercase letters/digits; survives norm() and slugs unchanged, e.g. "Đà Nẵng " + suffix. */
	public static String uniqueSuffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	public HotelBuilder hotel(String name) {
		return new HotelBuilder(name);
	}

	/** An ACTIVE room type selling {@code totalRooms} rooms per night unless inventory says otherwise. */
	public long roomType(long hotelId, int maxAdults, int maxChildren, int maxOccupancy, int totalRooms) {
		return jdbc.sql("""
				insert into room_types (hotel_id, name, max_adults, max_children, max_occupancy, total_rooms, status,
				                        created_at, updated_at)
				values (:hotelId, 'Room ' || :suffix, :maxAdults, :maxChildren, :maxOccupancy, :totalRooms, 'ACTIVE',
				        now(), now())
				returning id""")
			.param("hotelId", hotelId)
			.param("suffix", uniqueSuffix())
			.param("maxAdults", maxAdults)
			.param("maxChildren", maxChildren)
			.param("maxOccupancy", maxOccupancy)
			.param("totalRooms", totalRooms)
			.query(Long.class)
			.single();
	}

	public void inventory(long roomTypeId, LocalDate stayDate, int totalRooms, int bookedRooms, boolean closed) {
		jdbc.sql("""
				insert into room_inventory (room_type_id, stay_date, total_rooms, booked_rooms, is_closed)
				values (:roomTypeId, :stayDate, :totalRooms, :bookedRooms, :closed)""")
			.param("roomTypeId", roomTypeId)
			.param("stayDate", stayDate)
			.param("totalRooms", totalRooms)
			.param("bookedRooms", bookedRooms)
			.param("closed", closed)
			.update();
	}

	public void photo(long hotelId, String url, boolean cover, int sortOrder) {
		jdbc.sql("""
				insert into hotel_photos (hotel_id, url, is_cover, sort_order, created_at, updated_at)
				values (:hotelId, :url, :cover, :sortOrder, now(), now())""")
			.param("hotelId", hotelId)
			.param("url", url)
			.param("cover", cover)
			.param("sortOrder", sortOrder)
			.update();
	}

	public void hotelStatus(long hotelId, String status) {
		jdbc.sql("update hotels set status = :status where id = :id").param("status", status).param("id", hotelId).update();
	}

	public record CreatedHotel(long id, String slug, String name) {
	}

	/** Defaults: ACTIVE hotel in Vietnam without province, district, ward or ratings. */
	public final class HotelBuilder {

		private final String name;

		private String countryCode = "VN";

		private String stateProvince;

		private String city = "City " + uniqueSuffix();

		private String district;

		private String ward;

		private String status = "ACTIVE";

		private BigDecimal userRatingAvg;

		private int userRatingCount;

		private BigDecimal googleRating;

		private HotelBuilder(String name) {
			this.name = name;
		}

		public HotelBuilder country(String countryCode) {
			this.countryCode = countryCode;
			return this;
		}

		public HotelBuilder province(String stateProvince) {
			this.stateProvince = stateProvince;
			return this;
		}

		public HotelBuilder city(String city) {
			this.city = city;
			return this;
		}

		public HotelBuilder district(String district) {
			this.district = district;
			return this;
		}

		public HotelBuilder ward(String ward) {
			this.ward = ward;
			return this;
		}

		public HotelBuilder status(String status) {
			this.status = status;
			return this;
		}

		public HotelBuilder userRating(String average, int count) {
			this.userRatingAvg = new BigDecimal(average);
			this.userRatingCount = count;
			return this;
		}

		public HotelBuilder googleRating(String rating) {
			this.googleRating = new BigDecimal(rating);
			return this;
		}

		public CreatedHotel create() {
			long partnerId = jdbc.sql("""
					insert into partners (legal_name, contact_email, status, created_at, updated_at)
					values ('Partner ' || :suffix, 'partner-' || :suffix || '@test.local', 'APPROVED', now(), now())
					returning id""")
				.param("suffix", uniqueSuffix())
				.query(Long.class)
				.single();
			long addressId = jdbc.sql("""
					insert into addresses (line1, ward, district, city, state_province, country_code, created_at,
					                       updated_at)
					values ('1 Test Street', :ward, :district, :city, :stateProvince, :countryCode, now(), now())
					returning id""")
				.param("ward", ward)
				.param("district", district)
				.param("city", city)
				.param("stateProvince", stateProvince)
				.param("countryCode", countryCode)
				.query(Long.class)
				.single();
			String slug = "hotel-" + uniqueSuffix();
			long hotelId = jdbc.sql("""
					insert into hotels (partner_id, accommodation_type_id, name, slug, address_id, user_rating_avg,
					                    user_rating_count, google_rating, currency, timezone, status, created_at,
					                    updated_at)
					values (:partnerId, (select id from accommodation_types where code = 'hotel'), :name, :slug,
					        :addressId, :userRatingAvg, :userRatingCount, :googleRating, 'VND', 'Asia/Ho_Chi_Minh',
					        :status, now(), now())
					returning id""")
				.param("partnerId", partnerId)
				.param("name", name)
				.param("slug", slug)
				.param("addressId", addressId)
				.param("userRatingAvg", userRatingAvg)
				.param("userRatingCount", userRatingCount)
				.param("googleRating", googleRating)
				.param("status", status)
				.query(Long.class)
				.single();
			return new CreatedHotel(hotelId, slug, name);
		}

	}

}
