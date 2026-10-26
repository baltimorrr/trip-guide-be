package com.company.project.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Inserts hotel-catalog rows for integration tests. There are no hotel write APIs yet, so tests
 * build their data in SQL. The database is shared and never rolled back: give every place and hotel
 * a {@link #uniqueSuffix()} and assert only on your own rows.
 */
public final class CatalogFixtures {

	/** Night price of the rate plan {@link #roomType} adds, in VND. */
	public static final long DEFAULT_PRICE = 1_000_000;

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

	/**
	 * An ACTIVE room type selling {@code totalRooms} rooms per night unless inventory says otherwise, with one
	 * sellable rate plan: room only, {@link #DEFAULT_PRICE} VND per night, free cancellation.
	 */
	public long roomType(long hotelId, int maxAdults, int maxChildren, int maxOccupancy, int totalRooms) {
		long roomTypeId = bareRoomType(hotelId, maxAdults, maxChildren, maxOccupancy, totalRooms);
		ratePlan(roomTypeId).create();
		return roomTypeId;
	}

	/** Like {@link #roomType} but without a rate plan, so search cannot sell it. */
	public long bareRoomType(long hotelId, int maxAdults, int maxChildren, int maxOccupancy, int totalRooms) {
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

	public void roomPhoto(long hotelId, long roomTypeId, String url) {
		jdbc.sql("""
				insert into hotel_photos (hotel_id, room_type_id, url, created_at, updated_at)
				values (:hotelId, :roomTypeId, :url, now(), now())""")
			.param("hotelId", hotelId)
			.param("roomTypeId", roomTypeId)
			.param("url", url)
			.update();
	}

	public void hotelStatus(long hotelId, String status) {
		jdbc.sql("update hotels set status = :status where id = :id").param("status", status).param("id", hotelId).update();
	}

	/** Links a facility seeded by V6 ({@code wifi}, {@code pool}...) to the hotel. */
	public void hotelAmenity(long hotelId, String code) {
		jdbc.sql("""
				insert into hotel_amenities (hotel_id, amenity_id)
				values (:hotelId, (select id from amenities where code = :code))""")
			.param("hotelId", hotelId)
			.param("code", code)
			.update();
	}

	/** Links a room feature seeded by V6 ({@code balcony}, {@code sea_view}...) to the room type. */
	public void roomAmenity(long roomTypeId, String code) {
		jdbc.sql("""
				insert into room_type_amenities (room_type_id, amenity_id)
				values (:roomTypeId, (select id from amenities where code = :code))""")
			.param("roomTypeId", roomTypeId)
			.param("code", code)
			.update();
	}

	/** Gives the room type {@code quantity} beds of a type seeded by V6 ({@code single}, {@code king}...). */
	public void bed(long roomTypeId, String code, int quantity) {
		jdbc.sql("""
				insert into room_type_beds (room_type_id, bed_type_id, quantity)
				values (:roomTypeId, (select id from bed_types where code = :code), :quantity)""")
			.param("roomTypeId", roomTypeId)
			.param("code", code)
			.param("quantity", quantity)
			.update();
	}

	public RatePlanBuilder ratePlan(long roomTypeId) {
		return new RatePlanBuilder(roomTypeId);
	}

	/** A hotel's own policy; add its rules with {@link #cancellationRule}. No rules = free until check-in. */
	public long cancellationPolicy(String name, boolean refundable) {
		return jdbc.sql("""
				insert into cancellation_policies (name, is_refundable, created_at, updated_at)
				values (:name, :refundable, now(), now())
				returning id""")
			.param("name", name)
			.param("refundable", refundable)
			.query(Long.class)
			.single();
	}

	/** Cancelling less than {@code hours} before check-in costs {@code value} ({@code PERCENT}, {@code NIGHTS}, {@code FIXED}). */
	public void cancellationRule(long policyId, int hours, String penaltyType, String value) {
		jdbc.sql("""
				insert into cancellation_policy_rules (policy_id, hours_before_check_in, penalty_type, penalty_value,
				                                       created_at, updated_at)
				values (:policyId, :hours, :penaltyType, :value, now(), now())""")
			.param("policyId", policyId)
			.param("hours", hours)
			.param("penaltyType", penaltyType)
			.param("value", new BigDecimal(value))
			.update();
	}

	/** The owner's price for one night; {@code minNights} null keeps the plan's minimum stay. */
	public void nightPrice(long ratePlanId, LocalDate stayDate, long priceMinor, Integer minNights, boolean closed) {
		jdbc.sql("""
				insert into rate_plan_prices (rate_plan_id, stay_date, price_minor, min_nights, is_closed)
				values (:ratePlanId, :stayDate, :priceMinor, :minNights, :closed)""")
			.param("ratePlanId", ratePlanId)
			.param("stayDate", stayDate)
			.param("priceMinor", priceMinor)
			.param("minNights", minNights)
			.param("closed", closed)
			.update();
	}

	/** A price rule for {@code [from, to]} on the given ISO weekdays (none = every day). */
	public RateRuleBuilder rateRule(long ratePlanId, LocalDate from, LocalDate to, Integer... isoDays) {
		return new RateRuleBuilder(ratePlanId, from, to, isoDays);
	}

	/**
	 * A 10% tax for a whole country. Tax rules affect every hotel of that country, so tests use their own
	 * user-assigned codes ({@code XA}–{@code XZ}) and never {@code VN}.
	 */
	public void taxRule(String countryCode, boolean included, LocalDate validFrom, LocalDate validTo) {
		jdbc.sql("""
				insert into tax_rules (country_code, name, calc_type, value, is_included, valid_from, valid_to,
				                       created_at, updated_at)
				values (:countryCode, 'Tax ' || :suffix, 'PERCENT', 10, :included, :validFrom, :validTo, now(), now())""")
			.param("countryCode", countryCode)
			.param("suffix", uniqueSuffix())
			.param("included", included)
			.param("validFrom", validFrom)
			.param("validTo", validTo)
			.update();
	}

	public record CreatedHotel(long id, String slug, String name) {
	}

	/** Defaults: active, room only, {@link #DEFAULT_PRICE} VND per night, min 1 night, free cancellation. */
	public final class RatePlanBuilder {

		private final long roomTypeId;

		private String name = "Rate " + uniqueSuffix();

		private String board = "RO";

		private long priceMinor = DEFAULT_PRICE;

		private String currency = "VND";

		private int minNights = 1;

		private Long policyId;

		private boolean active = true;

		private RatePlanBuilder(long roomTypeId) {
			this.roomTypeId = roomTypeId;
		}

		public RatePlanBuilder name(String name) {
			this.name = name;
			return this;
		}

		public RatePlanBuilder board(String code) {
			this.board = code;
			return this;
		}

		public RatePlanBuilder price(long priceMinor) {
			this.priceMinor = priceMinor;
			return this;
		}

		public RatePlanBuilder currency(String currency) {
			this.currency = currency;
			return this;
		}

		public RatePlanBuilder minNights(int minNights) {
			this.minNights = minNights;
			return this;
		}

		public RatePlanBuilder policy(long policyId) {
			this.policyId = policyId;
			return this;
		}

		public RatePlanBuilder inactive() {
			this.active = false;
			return this;
		}

		public long create() {
			long policy = policyId != null ? policyId : cancellationPolicy("Free cancellation " + uniqueSuffix(), true);
			return jdbc.sql("""
					insert into rate_plans (room_type_id, name, board_type_id, cancellation_policy_id, base_price_minor,
					                        currency, min_nights, is_active, created_at, updated_at)
					values (:roomTypeId, :name, (select id from board_types where code = :board), :policyId, :priceMinor,
					        :currency, :minNights, :active, now(), now())
					returning id""")
				.param("roomTypeId", roomTypeId)
				.param("name", name)
				.param("board", board)
				.param("policyId", policy)
				.param("priceMinor", priceMinor)
				.param("currency", currency)
				.param("minNights", minNights)
				.param("active", active)
				.query(Long.class)
				.single();
		}

	}

	/** Pick one adjustment: {@link #percent}, {@link #amount} or {@link #setPrice}. Priority defaults to 0. */
	public final class RateRuleBuilder {

		private final long ratePlanId;

		private final LocalDate from;

		private final LocalDate to;

		private final String days;

		private String adjustment;

		private Long amountMinor;

		private BigDecimal percent;

		private int priority;

		private RateRuleBuilder(long ratePlanId, LocalDate from, LocalDate to, Integer... isoDays) {
			this.ratePlanId = ratePlanId;
			this.from = from;
			this.to = to;
			this.days = isoDays.length == 0 ? null
					: Arrays.stream(isoDays).map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
		}

		public RateRuleBuilder percent(String percent) {
			this.adjustment = "PERCENT";
			this.percent = new BigDecimal(percent);
			return this;
		}

		public RateRuleBuilder amount(long amountMinor) {
			this.adjustment = "AMOUNT";
			this.amountMinor = amountMinor;
			return this;
		}

		public RateRuleBuilder setPrice(long priceMinor) {
			this.adjustment = "SET_PRICE";
			this.amountMinor = priceMinor;
			return this;
		}

		public RateRuleBuilder priority(int priority) {
			this.priority = priority;
			return this;
		}

		public void create() {
			jdbc.sql("""
					insert into rate_rules (rate_plan_id, name, date_from, date_to, days_of_week, adjustment,
					                        amount_minor, percent, priority, created_at, updated_at)
					values (:ratePlanId, 'Rule ' || :suffix, :from, :to, cast(:days as smallint[]), :adjustment,
					        :amountMinor, :percent, :priority, now(), now())""")
				.param("ratePlanId", ratePlanId)
				.param("suffix", uniqueSuffix())
				.param("from", from)
				.param("to", to)
				.param("days", days)
				.param("adjustment", adjustment)
				.param("amountMinor", amountMinor)
				.param("percent", percent)
				.param("priority", priority)
				.update();
		}

	}

	/**
	 * Defaults: ACTIVE {@code hotel} in Vietnam selling in VND, without province, district, ward, stars, ratings or
	 * distance to the centre.
	 */
	public final class HotelBuilder {

		private final String name;

		private String accommodationType = "hotel";

		private Integer starRating;

		private Integer distanceToCenterM;

		private String currency = "VND";

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

		/** An accommodation type code seeded by V3: hotel, resort, apartment, villa, hostel. */
		public HotelBuilder type(String code) {
			this.accommodationType = code;
			return this;
		}

		public HotelBuilder stars(int stars) {
			this.starRating = stars;
			return this;
		}

		public HotelBuilder distance(int metres) {
			this.distanceToCenterM = metres;
			return this;
		}

		/** Selling currency; search only sells rate plans in it, so build those with the same currency. */
		public HotelBuilder currency(String currency) {
			this.currency = currency;
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
					insert into hotels (partner_id, accommodation_type_id, name, slug, star_rating, address_id,
					                    distance_to_center_m, user_rating_avg, user_rating_count, google_rating, currency,
					                    timezone, status, created_at, updated_at)
					values (:partnerId, (select id from accommodation_types where code = :type), :name, :slug, :stars,
					        :addressId, :distance, :userRatingAvg, :userRatingCount, :googleRating, :currency,
					        'Asia/Ho_Chi_Minh', :status, now(), now())
					returning id""")
				.param("partnerId", partnerId)
				.param("type", accommodationType)
				.param("name", name)
				.param("slug", slug)
				.param("stars", starRating)
				.param("addressId", addressId)
				.param("distance", distanceToCenterM)
				.param("userRatingAvg", userRatingAvg)
				.param("userRatingCount", userRatingCount)
				.param("googleRating", googleRating)
				.param("currency", currency)
				.param("status", status)
				.query(Long.class)
				.single();
			return new CreatedHotel(hotelId, slug, name);
		}

	}

}
