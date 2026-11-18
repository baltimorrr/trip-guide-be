package com.company.project.destination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Replaces the admin-owned fields of a destination. Limits mirror the destinations table. The Swagger examples are
 * the demo seed's values for Đà Nẵng, so send them with that destination's id.
 *
 * @param imageUrl http(s) URL, or null for no image
 * @param featuredRank position on the homepage, lowest first; null = not featured
 */
public record DestinationUpdateRequest(
		@Schema(example = "Đà Nẵng") @NotBlank @Size(max = 150) String name,
		@Schema(example = "https://picsum.photos/seed/vn-da-nang/1200/800")
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String imageUrl,
		@Schema(example = "1") @Min(1) @Max(999) Short featuredRank) {
}
