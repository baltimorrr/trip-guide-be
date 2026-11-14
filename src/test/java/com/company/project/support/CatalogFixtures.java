package com.company.project.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Inserts hotel-catalog rows, and the reviews of hotels, for integration tests. There are no hotel or review write
 * APIs yet, so tests build their data in SQL. The database is shared and never rolled back: give every place and
 * hotel a {@link #uniqueSuffix()} and assert only on your own rows.
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
		photo(hotelId, url, null, null, cover, sortOrder);
	}

	/** A photo of the hotel itself; {@code category} (pool, lobby...) and {@code caption} may be null. */
	public void photo(long hotelId, String url, String category, String caption, boolean cover, int sortOrder) {
		jdbc.sql("""
				insert into hotel_photos (hotel_id, url, category, caption, is_cover, sort_order, created_at, updated_at)
				values (:hotelId, :url, :category, :caption, :cover, :sortOrder, now(), now())""")
			.param("hotelId", hotelId)
			.param("url", url)
			.param("category", category)
			.param("caption", caption)
			.param("cover", cover)
			.param("sortOrder", sortOrder)
			.update();
	}

	public void roomPhoto(long hotelId, long roomTypeId, String url) {
		roomPhoto(hotelId, roomTypeId, url, null, 0);
	}

	public void roomPhoto(long hotelId, long roomTypeId, String url, String category, int sortOrder) {
		jdbc.sql("""
				insert into hotel_photos (hotel_id, room_type_id, url, category, sort_order, created_at, updated_at)
				values (:hotelId, :roomTypeId, :url, :category, :sortOrder, now(), now())""")
			.param("hotelId", hotelId)
			.param("roomTypeId", roomTypeId)
			.param("url", url)
			.param("category", category)
			.param("sortOrder", sortOrder)
			.update();
	}

	public void hotelStatus(long hotelId, String status) {
		jdbc.sql("update hotels set status = :status where id = :id").param("status", status).param("id", hotelId).update();
	}

	public void roomTypeStatus(long roomTypeId, String status) {
		jdbc.sql("update room_types set status = :status where id = :id")
			.param("status", status)
			.param("id", roomTypeId)
			.update();
	}

	/** What the hotel page shows about a room type besides its capacity; {@code sizeSqm} like "28.5", or null. */
	public void roomTypeDetails(long roomTypeId, String description, String sizeSqm, int sortOrder) {
		jdbc.sql("""
				update room_types set description = :description, size_sqm = :sizeSqm, sort_order = :sortOrder
				where id = :id""")
			.param("description", description)
			.param("sizeSqm", sizeSqm == null ? null : new BigDecimal(sizeSqm))
			.param("sortOrder", sortOrder)
			.param("id", roomTypeId)
			.update();
	}

	/** Links a facility seeded by V6 ({@code wifi}, {@code pool}...) to the hotel, free of charge. */
	public void hotelAmenity(long hotelId, String code) {
		hotelAmenity(hotelId, code, true);
	}

	public void hotelAmenity(long hotelId, String code, boolean free) {
		jdbc.sql("""
				insert into hotel_amenities (hotel_id, amenity_id, is_free)
				values (:hotelId, (select id from amenities where code = :code), :free)""")
			.param("hotelId", hotelId)
			.param("code", code)
			.param("free", free)
			.update();
	}

	/** A policy paragraph; {@code type} is a {@code HotelPolicyType} name (CHILDREN, PETS...). */
	public void hotelPolicy(long hotelId, String type, String description, int sortOrder) {
		jdbc.sql("""
				insert into hotel_policies (hotel_id, policy_type, description, sort_order, created_at, updated_at)
				values (:hotelId, :type, :description, :sortOrder, now(), now())""")
			.param("hotelId", hotelId)
			.param("type", type)
			.param("description", description)
			.param("sortOrder", sortOrder)
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

	/** The rate plan {@link #roomType} added: the room type's first one. */
	public long defaultRatePlan(long roomTypeId) {
		return jdbc.sql("select min(id) from rate_plans where room_type_id = :roomTypeId")
			.param("roomTypeId", roomTypeId)
			.query(Long.class)
			.single();
	}

	/**
	 * A cart line written straight to the database, for lines the API would refuse (a check-in already past). It
	 * creates the user's cart if needed. Adults per room, no children, the stay priced at {@link #DEFAULT_PRICE} a
	 * night in VND.
	 */
	public long cartItem(long userId, long ratePlanId, LocalDate checkIn, LocalDate checkOut, int quantity, int adults) {
		jdbc.sql("""
				insert into carts (user_id, created_at, updated_at) values (:userId, now(), now())
				on conflict (user_id) do nothing""")
			.param("userId", userId)
			.update();
		return jdbc.sql("""
				insert into cart_items (cart_id, rate_plan_id, check_in, check_out, quantity, adults, price_at_add_minor,
				                        currency, created_at, updated_at)
				values ((select id from carts where user_id = :userId), :ratePlanId, :checkIn, :checkOut, :quantity,
				        :adults, :price * (cast(:checkOut as date) - cast(:checkIn as date)), 'VND', now(), now())
				returning id""")
			.param("userId", userId)
			.param("ratePlanId", ratePlanId)
			.param("checkIn", checkIn)
			.param("checkOut", checkOut)
			.param("quantity", quantity)
			.param("adults", adults)
			.param("price", DEFAULT_PRICE)
			.query(Long.class)
			.single();
	}

	/** The id of the account with this email. */
	public long userId(String email) {
		return jdbc.sql("select id from users where email = :email").param("email", email).query(Long.class).single();
	}

	/** An account that only owns reviews: its password hash matches nothing, so it can't sign in. */
	public long user() {
		return jdbc.sql("""
				insert into users (email, password_hash, full_name, created_at, updated_at)
				values ('reviewer-' || :suffix || '@test.local', '{noop}unused', 'Reviewer ' || :suffix, now(), now())
				returning id""")
			.param("suffix", uniqueSuffix())
			.query(Long.class)
			.single();
	}

	/** A review of the hotel by {@code userId} with a score from 1 to 10. */
	public ReviewBuilder review(long hotelId, long userId, int score) {
		return new ReviewBuilder(hotelId, userId, score);
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

	/** Defaults: published, written now, without title, text, traveller type or owner reply. */
	public final class ReviewBuilder {

		private final long hotelId;

		private final long userId;

		private final int score;

		private String title;

		private String body;

		private String travellerType;

		private String status = "PUBLISHED";

		private Instant createdAt = Instant.now();

		private String ownerReply;

		private Instant ownerRepliedAt;

		private ReviewBuilder(long hotelId, long userId, int score) {
			this.hotelId = hotelId;
			this.userId = userId;
			this.score = score;
		}

		public ReviewBuilder title(String title) {
			this.title = title;
			return this;
		}

		public ReviewBuilder body(String body) {
			this.body = body;
			return this;
		}

		/** A {@code TravellerType} name: SOLO, COUPLE, FAMILY, BUSINESS, GROUP. */
		public ReviewBuilder travellerType(String travellerType) {
			this.travellerType = travellerType;
			return this;
		}

		public ReviewBuilder hidden() {
			this.status = "HIDDEN";
			return this;
		}

		public ReviewBuilder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public ReviewBuilder ownerReply(String reply, Instant repliedAt) {
			this.ownerReply = reply;
			this.ownerRepliedAt = repliedAt;
			return this;
		}

		public long create() {
			// No bookings table yet, so booking_id has no foreign key: any number no other review uses will do.
			return jdbc.sql("""
					insert into reviews (booking_id, hotel_id, user_id, score, title, body, traveller_type, status,
					                     owner_reply, owner_replied_at, created_at, updated_at)
					values (:bookingId, :hotelId, :userId, :score, :title, :body, :travellerType, :status, :ownerReply,
					        :ownerRepliedAt, :createdAt, :createdAt)
					returning id""")
				.param("bookingId", ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE))
				.param("hotelId", hotelId)
				.param("userId", userId)
				.param("score", score)
				.param("title", title)
				.param("body", body)
				.param("travellerType", travellerType)
				.param("status", status)
				.param("ownerReply", ownerReply)
				.param("ownerRepliedAt", ownerRepliedAt == null ? null : ownerRepliedAt.atOffset(ZoneOffset.UTC))
				.param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
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

		private String description;

		private BigDecimal latitude;

		private BigDecimal longitude;

		private LocalTime checkInUntil;

		// The column's default, so a hotel built without it reads like one inserted without it.
		private Integer minCheckInAge = 18;

		private HotelBuilder(String name) {
			this.name = name;
		}

		public HotelBuilder description(String description) {
			this.description = description;
			return this;
		}

		public HotelBuilder coordinates(String latitude, String longitude) {
			this.latitude = new BigDecimal(latitude);
			this.longitude = new BigDecimal(longitude);
			return this;
		}

		public HotelBuilder checkInUntil(LocalTime checkInUntil) {
			this.checkInUntil = checkInUntil;
			return this;
		}

		public HotelBuilder minCheckInAge(Integer minCheckInAge) {
			this.minCheckInAge = minCheckInAge;
			return this;
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
					insert into addresses (line1, ward, district, city, state_province, country_code, latitude, longitude,
					                       created_at, updated_at)
					values ('1 Test Street', :ward, :district, :city, :stateProvince, :countryCode, :latitude, :longitude,
					        now(), now())
					returning id""")
				.param("ward", ward)
				.param("district", district)
				.param("city", city)
				.param("stateProvince", stateProvince)
				.param("countryCode", countryCode)
				.param("latitude", latitude)
				.param("longitude", longitude)
				.query(Long.class)
				.single();
			String slug = "hotel-" + uniqueSuffix();
			long hotelId = jdbc.sql("""
					insert into hotels (partner_id, accommodation_type_id, name, slug, description, star_rating, address_id,
					                    distance_to_center_m, user_rating_avg, user_rating_count, google_rating, currency,
					                    timezone, check_in_until, min_check_in_age, status, created_at, updated_at)
					values (:partnerId, (select id from accommodation_types where code = :type), :name, :slug, :description,
					        :stars, :addressId, :distance, :userRatingAvg, :userRatingCount, :googleRating, :currency,
					        'Asia/Ho_Chi_Minh', :checkInUntil, :minCheckInAge, :status, now(), now())
					returning id""")
				.param("partnerId", partnerId)
				.param("type", accommodationType)
				.param("name", name)
				.param("slug", slug)
				.param("description", description)
				.param("stars", starRating)
				.param("addressId", addressId)
				.param("distance", distanceToCenterM)
				.param("userRatingAvg", userRatingAvg)
				.param("userRatingCount", userRatingCount)
				.param("googleRating", googleRating)
				.param("currency", currency)
				.param("checkInUntil", checkInUntil)
				.param("minCheckInAge", minCheckInAge)
				.param("status", status)
				.query(Long.class)
				.single();
			return new CreatedHotel(hotelId, slug, name);
		}

	}

}
