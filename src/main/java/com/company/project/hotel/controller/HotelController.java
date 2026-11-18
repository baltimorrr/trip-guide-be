package com.company.project.hotel.controller;

import com.company.project.hotel.dto.HotelDetail;
import com.company.project.hotel.dto.RoomList;
import com.company.project.hotel.dto.StayRequest;
import com.company.project.hotel.services.HotelService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public hotel page. A search result links here by the hotel's slug with its search's query parameters, which the
 * client passes on to the dated parts of the page.
 */
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

	private final HotelService hotelService;

	/** Overview, gallery, facilities and policies; 404 when the hotel isn't listed. */
	@GetMapping("/{slug}")
	@SecurityRequirements // public: no bearer token in the API docs
	public HotelDetail detail(@Parameter(example = "my-khe-beach-resort-demo") @PathVariable String slug) {
		return hotelService.detail(slug);
	}

	/**
	 * {@code ?checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2&childAges=7}, the search's stay parameters. Every
	 * room type with a room free each night and every rate it can be sold on, per room: price per night and for the
	 * stay, board, free-cancellation deadline and the penalties after it. Room types that can't hold the guests of one
	 * room are listed last with {@code fitsGuests: false}. Changing the dates on the page is calling this again.
	 */
	@GetMapping("/{slug}/rooms")
	@SecurityRequirements // public: no bearer token in the API docs
	public RoomList rooms(@Parameter(example = "my-khe-beach-resort-demo") @PathVariable String slug,
			@Valid @ParameterObject StayRequest request) {
		return hotelService.rooms(slug, request);
	}

}
