package com.company.project.hotel.services;

import com.company.project.hotel.dto.HotelDetail;
import com.company.project.hotel.dto.RoomList;
import com.company.project.hotel.dto.StayRequest;

/** The public hotel page, opened by the hotel's slug from a search result or a shared link. */
public interface HotelService {

	/**
	 * The hotel's overview, gallery, facilities and policies (C-HOTEL-01/02/03). Throws ResourceNotFoundException
	 * (404) for a hotel that doesn't exist or isn't listed.
	 */
	HotelDetail detail(String slug);

	/**
	 * Every room type that can be sold for the stay, with all its rates and their cancellation terms
	 * (C-HOTEL-04/06), by the search's availability and price rules. Throws BusinessException (400) for a stay the
	 * search would reject and ResourceNotFoundException (404) like {@link #detail}.
	 */
	RoomList rooms(String slug, StayRequest request);

}
