package com.company.project.hotel.repository.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.company.project.hotel.repository.FilterGroup;
import com.company.project.hotel.repository.HotelFilter;
import com.company.project.hotel.repository.HotelOrder;
import jakarta.persistence.Query;

/**
 * Renders a {@link HotelFilter} as predicates of the priced availability query and collects the parameters they use,
 * so the SQL text and its bound parameters always match. Only active filters are rendered: an empty set would become
 * {@code in ()}, and Hibernate rejects binding a parameter the text does not use. Request values only ever travel as
 * bound parameters.
 * <p>
 * Alias contract (see {@link HotelRepositoryImpl}): {@code h} = hotels, {@code c} = a {@code hotel_rows} row,
 * {@code o} = an {@code offers} row, {@code v} = an {@code available} or {@code results} row.
 */
final class HotelFilterSql {

	private final HotelFilter filter;

	private final Map<String, Object> params = new HashMap<>();

	HotelFilterSql(HotelFilter filter) {
		this.filter = filter;
	}

	/** {@code " and "} plus the hotel-name condition on {@code h}, or {@code ""} when the name is not filtered. */
	String nameCondition() {
		if (filter.nameQuery() == null) {
			return "";
		}
		return " and " + bind("norm(h.name) like concat('%', norm(:name), '%') escape '\\'", "name", filter.nameQuery());
	}

	/** The active hotel filters on {@code c}, joined with {@code and}; {@code true} when there is none. */
	String hotelPredicates() {
		return predicates(false, null);
	}

	/** Like {@link #hotelPredicates()} without the group's own filter: what its options are counted against. */
	String hotelPredicates(FilterGroup except) {
		return predicates(false, except);
	}

	/** The active offer filters on {@code o}, joined with {@code and}; {@code true} when there is none. */
	String offerPredicates() {
		return predicates(true, null);
	}

	/** Like {@link #offerPredicates()} without the group's own filter: what its options are counted against. */
	String offerPredicates(FilterGroup except) {
		return predicates(true, except);
	}

	/**
	 * The price range on {@code v}: the whole offer's total for the stay, in the price currency, between the nightly
	 * bounds times {@code nights}. {@code true} without bounds.
	 */
	String price(int nights) {
		List<String> parts = new ArrayList<>();
		if (filter.minNightMinor() != null) {
			parts.add(bind("v.total >= :minTotal", "minTotal", filter.minNightMinor() * nights));
		}
		if (filter.maxNightMinor() != null) {
			parts.add(bind("v.total <= :maxTotal", "maxTotal", filter.maxNightMinor() * nights));
		}
		if (parts.isEmpty()) {
			return "true";
		}
		// :priceCurrency is bound with the stay, since every query selects it.
		parts.addFirst("v.currency = :priceCurrency");
		return String.join(" and ", parts);
	}

	/**
	 * The {@code order by} items for a page of {@code results v} joined to {@code hotels h}; the hotel id last keeps
	 * the order stable across pages. The review minimum is a number from configuration, never request data.
	 */
	static String order(HotelOrder order) {
		if (order.sort() == null) {
			return "h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id";
		}
		return switch (order.sort()) {
			case PRICE -> "v.in_price_currency desc, v.currency, v.total, h.id";
			case RATING -> "(h.user_rating_count >= " + order.ratingMinReviews()
					+ ") desc, h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id";
			case DISTANCE -> "h.distance_to_center_m nulls last, h.id";
		};
	}

	/** Binds every parameter rendered so far. */
	Query bind(Query query) {
		params.forEach(query::setParameter);
		return query;
	}

	private String predicates(boolean offerLevel, FilterGroup except) {
		String joined = Arrays.stream(FilterGroup.values())
			.filter(group -> group.offerLevel() == offerLevel && group != except)
			.map(this::predicate)
			.filter(Objects::nonNull)
			.collect(Collectors.joining(" and "));
		return joined.isEmpty() ? "true" : joined;
	}

	// Null when the filter is not active.
	private String predicate(FilterGroup group) {
		return switch (group) {
			case STARS -> in("c.star_rating", "stars", filter.stars());
			case GOOGLE_RATING -> filter.minGoogleRating() == null ? null
					: bind("c.google_rating >= :minGoogleRating", "minGoogleRating", filter.minGoogleRating());
			case USER_RATING -> filter.minUserRating() == null ? null
					: bind("c.user_rating_avg >= :minUserRating", "minUserRating", filter.minUserRating());
			case ACCOMMODATION_TYPE -> in("c.accommodation_type", "accommodationTypes", filter.accommodationTypes());
			case FACILITIES -> containsAll("c.facilities", "facilities", filter.facilities());
			case DISTANCE -> filter.maxDistanceM() == null ? null
					: bind("c.distance_to_center_m <= :maxDistanceM", "maxDistanceM", filter.maxDistanceM());
			case BOARD_TYPE -> in("o.board_type", "boardTypes", filter.boardTypes());
			case CANCELLATION -> in("o.cancellation", "cancellation", filter.cancellation());
			case BED_TYPE -> containsAny("o.bed_types", "bedTypes", filter.bedTypes());
			case ROOM_FEATURES -> containsAll("o.room_features", "roomFeatures", filter.roomFeatures());
		};
	}

	private String in(String column, String name, Collection<?> values) {
		return values.isEmpty() ? null : bind(column + " in (:" + name + ")", name, values);
	}

	// Array columns are matched through "in (:list)": Hibernate expands a list inside array[...] into a row value.
	private String containsAny(String arrayColumn, String name, Collection<String> values) {
		if (values.isEmpty()) {
			return null;
		}
		return bind("exists (select 1 from unnest(%s) as x(code) where x.code in (:%s))".formatted(arrayColumn, name),
				name, values);
	}

	// The codes of an array column are distinct, and so are the values (a set), so counting matches finds "all".
	private String containsAll(String arrayColumn, String name, Collection<String> values) {
		if (values.isEmpty()) {
			return null;
		}
		params.put(name + "Count", values.size());
		return bind("(select count(*) from unnest(%s) as x(code) where x.code in (:%s)) = :%sCount"
			.formatted(arrayColumn, name, name), name, values);
	}

	private String bind(String sql, String name, Object value) {
		params.put(name, value);
		return sql;
	}

}
