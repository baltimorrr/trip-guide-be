package com.company.project.booking.repository;

import java.util.Collection;

import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bookings. Hand-written queries are in {@link BookingRepositoryCustom}. */
public interface BookingRepository extends JpaRepository<Booking, Long>, BookingRepositoryCustom {

	boolean existsByReference(String reference);

	boolean existsByUserIdAndStatusIn(Long userId, Collection<BookingStatus> statuses);

}
