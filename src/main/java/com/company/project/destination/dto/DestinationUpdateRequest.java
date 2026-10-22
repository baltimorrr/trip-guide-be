package com.company.project.destination.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Replaces the admin-owned fields of a destination. Limits mirror the destinations table.
 *
 * @param imageUrl http(s) URL, or null for no image
 * @param featuredRank position on the homepage, lowest first; null = not featured
 */
public record DestinationUpdateRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String imageUrl,
		@Min(1) @Max(999) Short featuredRank) {
}
