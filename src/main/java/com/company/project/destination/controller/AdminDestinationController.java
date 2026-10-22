package com.company.project.destination.controller;

import com.company.project.common.dto.PageResponse;
import com.company.project.destination.dto.AdminDestinationResponse;
import com.company.project.destination.dto.DestinationSyncResult;
import com.company.project.destination.dto.DestinationUpdateRequest;
import com.company.project.destination.services.DestinationService;
import com.company.project.destination.services.DestinationSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admins pick which destinations the homepage features, their order, image and display name. */
@RestController
@RequestMapping("/api/admin/destinations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDestinationController {

	private final DestinationService destinationService;

	private final DestinationSyncService syncService;

	/** {@code ?q=da nang&page=0&size=20}: featured first, then by hotel count; {@code sort} is ignored. */
	@GetMapping
	public PageResponse<AdminDestinationResponse> list(@RequestParam(defaultValue = "") String q,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(destinationService.adminSearch(q, pageable));
	}

	@PutMapping("/{id}")
	public AdminDestinationResponse update(@PathVariable Long id, @Valid @RequestBody DestinationUpdateRequest request) {
		return destinationService.update(id, request);
	}

	/** Runs the sync now instead of waiting for the schedule, e.g. right after hotels went live. */
	@PostMapping("/sync")
	public DestinationSyncResult sync() {
		return syncService.sync();
	}

}
