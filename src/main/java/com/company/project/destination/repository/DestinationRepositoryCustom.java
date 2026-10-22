package com.company.project.destination.repository;

import java.util.List;

import com.company.project.destination.model.Destination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Hand-written destination queries, implemented in {@code impl/DestinationRepositoryImpl}. */
public interface DestinationRepositoryCustom {

	/** Destinations admins featured on the homepage, in their order. */
	List<Destination> findFeatured(int limit);

	/** Cities with the most listed hotels; the homepage shows these while nothing is featured. */
	List<Destination> findTopCities(int limit);

	/**
	 * Live destinations whose normalized name starts with norm(prefix); "" matches all. Featured first, then by
	 * hotel count; any sort in the Pageable is ignored.
	 */
	Page<Destination> searchByNamePrefix(String prefix, Pageable pageable);

	/** Country rows still named by their ISO code, as the sync first creates them. */
	List<Destination> findCountriesNamedByCode();

	/**
	 * Places with a listed hotel whose name is similar to the typed text (pg_trgm word similarity), closest
	 * first, then more hotels. {@code query} is plain text: no LIKE escaping, since pg_trgm ignores \, % and _.
	 */
	List<DestinationSuggestionView> suggest(String query, int limit);

}
