package com.company.project.destination.repository;

import java.util.Optional;

import com.company.project.destination.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Destinations. Spring Data generates CRUD and the derived queries declared here; the hand-written queries are
 * declared in {@link DestinationRepositoryCustom} and implemented in {@code impl/DestinationRepositoryImpl}.
 */
public interface DestinationRepository extends JpaRepository<Destination, Long>, DestinationRepositoryCustom {

	Optional<Destination> findBySlugAndDeletedAtIsNull(String slug);

	long countByDeletedAtIsNullAndHotelCountGreaterThan(int hotelCount);

}
