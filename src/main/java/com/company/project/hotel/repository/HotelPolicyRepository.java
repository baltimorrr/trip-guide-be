package com.company.project.hotel.repository;

import java.util.List;

import com.company.project.hotel.model.HotelPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/** Hotel policy paragraphs. Spring Data generates CRUD and the derived query. */
public interface HotelPolicyRepository extends JpaRepository<HotelPolicy, Long> {

	/** The hotel's policies in its own order. */
	List<HotelPolicy> findByHotelIdOrderBySortOrderAscIdAsc(Long hotelId);

}
