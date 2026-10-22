package com.company.project.hotel.repository;

/**
 * JPQL fragments shared by the hotel-catalog queries, so the sync counts, popular hotels and suggestions
 * apply the same rules.
 * <p>
 * Alias contract: {@code h} = Hotel, {@code a} = its Address, {@code d} = Destination.
 */
public final class HotelJpql {

	/** Hotel {@code h} is visible to travellers. */
	public static final String HOTEL_LISTED =
			"h.status = com.company.project.hotel.model.ListingStatus.ACTIVE and h.deletedAt is null";

	/**
	 * Address {@code a} lies inside destination {@code d}: same country, then every key down to the
	 * destination's level. Renders the same expressions as the {@code addresses_place_match} index.
	 * {@link HotelSql#ADDRESS_IN_DESTINATION} is the SQL twin for the native availability query; change both.
	 */
	public static final String ADDRESS_IN_DESTINATION = """
			a.countryCode = d.countryCode
			and (d.type = com.company.project.destination.model.DestinationType.COUNTRY or (
			    coalesce(norm(a.stateProvince), '') = d.provinceKey
			    and coalesce(norm(a.city), '') = d.cityKey
			    and (d.type = com.company.project.destination.model.DestinationType.CITY or (
			        coalesce(norm(a.district), '') = d.districtKey
			        and (d.type = com.company.project.destination.model.DestinationType.DISTRICT
			             or coalesce(norm(a.ward), '') = d.wardKey)))))""";

	/** Select and joins of a {@link HotelCardView}; append the where and order by clauses. */
	public static final String CARD_SELECT = """
			select new com.company.project.hotel.repository.HotelCardView(
			       h.id, h.slug, h.name, t.code, h.starRating,
			       (select p.url from HotelPhoto p where p.hotel = h and p.roomTypeId is null
			        order by p.cover desc, p.sortOrder, p.id limit 1),
			       a.district, a.city, a.countryCode, h.googleRating, h.googleRatingCount, h.userRatingAvg,
			       h.userRatingCount, h.distanceToCenterM)
			from Hotel h
			join h.address a
			join h.accommodationType t
			""";

	private HotelJpql() {
	}

}
