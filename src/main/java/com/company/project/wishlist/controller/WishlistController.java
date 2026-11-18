package com.company.project.wishlist.controller;

import com.company.project.common.dto.PageResponse;
import com.company.project.wishlist.dto.WishlistHotel;
import com.company.project.wishlist.services.WishlistService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's wishlist (C-SRCH-08). Search results don't say which hotels are saved: clients draw the
 * hearts on result cards from the hotel ids listed here.
 */
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

	private final WishlistService wishlistService;

	/** Saved hotels, newest first; {@code sort} is ignored. Hotels no longer listed come with {@code available: false}. */
	@GetMapping
	public PageResponse<WishlistHotel> list(@AuthenticationPrincipal Jwt jwt,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(wishlistService.list(userId(jwt), pageable));
	}

	/** 204 whether the hotel is new to the wishlist or already in it; 404 when it isn't listed. */
	@PutMapping("/{hotelId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void save(@AuthenticationPrincipal Jwt jwt, @Parameter(example = "1") @PathVariable long hotelId) {
		wishlistService.save(userId(jwt), hotelId);
	}

	/** 204 whether or not the hotel was saved. */
	@DeleteMapping("/{hotelId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@AuthenticationPrincipal Jwt jwt, @Parameter(example = "1") @PathVariable long hotelId) {
		wishlistService.remove(userId(jwt), hotelId);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
