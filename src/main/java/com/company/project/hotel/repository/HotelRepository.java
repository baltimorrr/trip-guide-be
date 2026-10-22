package com.company.project.hotel.repository;

import java.util.Optional;

import com.company.project.hotel.model.Hotel;
import com.company.project.hotel.model.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Hotels. Spring Data generates CRUD and the derived queries declared here; the hand-written queries are
 * declared in {@link HotelRepositoryCustom} and implemented in {@code impl/HotelRepositoryImpl}.
 */
public interface HotelRepository extends JpaRepository<Hotel, Long>, HotelRepositoryCustom {

	Optional<Hotel> findBySlugAndStatusAndDeletedAtIsNull(String slug, ListingStatus status);

}
