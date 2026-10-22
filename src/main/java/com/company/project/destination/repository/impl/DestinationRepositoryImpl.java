package com.company.project.destination.repository.impl;

import java.util.List;

import com.company.project.destination.model.Destination;
import com.company.project.destination.repository.DestinationRepositoryCustom;
import com.company.project.destination.repository.DestinationSuggestionView;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * Handwritten destination queries, all JPQL. Spring Data finds this class by its name
 * ({@code DestinationRepository} + "Impl") and merges it into the {@code DestinationRepository} bean.
 */
@RequiredArgsConstructor
public class DestinationRepositoryImpl implements DestinationRepositoryCustom {

	private static final String FEATURED = """
			select d from Destination d
			where d.deletedAt is null and d.hotelCount > 0 and d.featuredRank is not null
			order by d.featuredRank, d.name, d.id""";

	private static final String TOP_CITIES = """
			select d from Destination d
			where d.deletedAt is null and d.hotelCount > 0
			  and d.type = com.company.project.destination.model.DestinationType.CITY
			order by d.hotelCount desc, d.name, d.id""";

	private static final String NAME_PREFIX = """
			from Destination d
			where d.deletedAt is null and d.normalizedName like concat(norm(:prefix), '%')""";

	private static final String BY_NAME_PREFIX = "select d " + NAME_PREFIX
			+ "\norder by d.featuredRank nulls last, d.hotelCount desc, d.name, d.id";

	private static final String COUNT_BY_NAME_PREFIX = "select count(d) " + NAME_PREFIX;

	private static final String COUNTRIES_NAMED_BY_CODE = """
			select d from Destination d
			where d.deletedAt is null and d.type = com.company.project.destination.model.DestinationType.COUNTRY
			  and d.name = d.countryCode""";

	/**
	 * Word similarity on the name finds a half-typed word ("hai ch") and a small typo ("hai chua"). Among equally
	 * close names the place with more hotels wins, which puts a city before the districts inside it. The country
	 * row is joined only for its current name, shown in the label.
	 */
	private static final String SUGGEST = """
			select new com.company.project.destination.repository.DestinationSuggestionView(
			       d.slug, d.type, d.name, d.hotelCount, d.district, d.city, c.name)
			from Destination d
			left join Destination c on c.countryCode = d.countryCode and c.deletedAt is null
			     and c.type = com.company.project.destination.model.DestinationType.COUNTRY
			where d.deletedAt is null and d.hotelCount > 0
			  and word_similar(norm(:query), d.normalizedName)
			order by word_similarity(norm(:query), d.normalizedName) desc, d.hotelCount desc, d.name, d.id""";

	private final EntityManager entityManager;

	@Override
	public List<Destination> findFeatured(int limit) {
		return entityManager.createQuery(FEATURED, Destination.class).setMaxResults(limit).getResultList();
	}

	@Override
	public List<Destination> findTopCities(int limit) {
		return entityManager.createQuery(TOP_CITIES, Destination.class).setMaxResults(limit).getResultList();
	}

	@Override
	public Page<Destination> searchByNamePrefix(String prefix, Pageable pageable) {
		List<Destination> content = entityManager.createQuery(BY_NAME_PREFIX, Destination.class)
			.setParameter("prefix", prefix)
			.setFirstResult((int) pageable.getOffset())
			.setMaxResults(pageable.getPageSize())
			.getResultList();
		// Counts only when the page alone cannot tell the total.
		return PageableExecutionUtils.getPage(content, pageable,
				() -> entityManager.createQuery(COUNT_BY_NAME_PREFIX, Long.class)
					.setParameter("prefix", prefix)
					.getSingleResult());
	}

	@Override
	public List<Destination> findCountriesNamedByCode() {
		return entityManager.createQuery(COUNTRIES_NAMED_BY_CODE, Destination.class).getResultList();
	}

	@Override
	public List<DestinationSuggestionView> suggest(String query, int limit) {
		return entityManager.createQuery(SUGGEST, DestinationSuggestionView.class)
			.setParameter("query", query)
			.setMaxResults(limit)
			.getResultList();
	}

}
