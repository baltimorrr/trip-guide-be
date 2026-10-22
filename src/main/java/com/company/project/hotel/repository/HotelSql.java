package com.company.project.hotel.repository;

/**
 * SQL fragments for the few native queries: the availability check and the destination sync's bulk insert.
 * Everything else is JPQL ({@link HotelJpql}).
 * <p>
 * Alias contract: {@code h} = hotels, {@code a} = addresses, {@code d} = destinations.
 */
public final class HotelSql {

	/** Hotel {@code h} is visible to travellers. */
	public static final String HOTEL_LISTED = "h.status = 'ACTIVE' and h.deleted_at is null";

	/** SQL twin of {@link HotelJpql#ADDRESS_IN_DESTINATION}; change both. */
	public static final String ADDRESS_IN_DESTINATION = """
			a.country_code = d.country_code
			and (d.type = 'COUNTRY' or (
			    coalesce(norm(a.state_province), '') = d.province_key
			    and coalesce(norm(a.city), '') = d.city_key
			    and (d.type = 'CITY' or (
			        coalesce(norm(a.district), '') = d.district_key
			        and (d.type = 'DISTRICT' or coalesce(norm(a.ward), '') = d.ward_key)))))""";

	private HotelSql() {
	}

}
