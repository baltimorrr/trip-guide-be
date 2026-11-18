package com.company.project.destination.controller;

import java.util.List;

import com.company.project.destination.dto.DestinationResponse;
import com.company.project.destination.dto.PopularDestinationResponse;
import com.company.project.destination.services.DestinationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public destination reads for the homepage. */
@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationController {

	private final DestinationService destinationService;

	/** {@code ?limit=8&hotels=4}: up to 20 destinations with up to 10 hotels each. */
	@GetMapping("/popular")
	@SecurityRequirements // public: no bearer token in the API docs
	public List<PopularDestinationResponse> popular(@RequestParam(defaultValue = "8") int limit,
			@RequestParam(defaultValue = "4") int hotels) {
		return destinationService.popular(limit, hotels);
	}

	@GetMapping("/{slug}")
	@SecurityRequirements // public: no bearer token in the API docs
	public DestinationResponse get(@Parameter(example = "vn-da-nang") @PathVariable String slug) {
		return destinationService.findBySlug(slug);
	}

}
