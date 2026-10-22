package com.company.project.destination.services.impl;

import java.util.Locale;

import com.company.project.destination.dto.DestinationSyncResult;
import com.company.project.destination.model.Destination;
import com.company.project.destination.repository.DestinationRepository;
import com.company.project.destination.repository.DestinationSyncRepository;
import com.company.project.destination.services.DestinationSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationSyncServiceImpl implements DestinationSyncService {

	private final DestinationSyncRepository syncRepository;

	private final DestinationRepository destinations;

	@Override
	@Transactional
	public DestinationSyncResult sync() {
		syncRepository.lock();
		int created = syncRepository.insertNewPlaces();
		syncRepository.refreshSpellings();
		syncRepository.refreshHotelCounts();
		nameNewCountries();
		DestinationSyncResult result = new DestinationSyncResult(created,
				Math.toIntExact(destinations.countByDeletedAtIsNullAndHotelCountGreaterThan(0)));
		log.info("Destination sync: created={}, withHotels={}", result.created(), result.withHotels());
		return result;
	}

	// The sync names a new country by its ISO code; replace that with the English name when the JDK knows it.
	// Admins can still rename it afterwards, and a renamed country no longer matches this query.
	private void nameNewCountries() {
		for (Destination country : destinations.findCountriesNamedByCode()) {
			String name = Locale.of("", country.getCountryCode()).getDisplayCountry(Locale.ENGLISH);
			if (StringUtils.hasText(name) && !name.equals(country.getCountryCode())) {
				country.rename(name);
			}
		}
	}

}
