package com.company.project.booking.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The lead guest of each room of a cart line, first room first; at most one name per room. */
public record LeadGuests(@NotNull Long cartItemId, @NotNull @Size(max = 8) List<@NotBlank @Size(max = 100) String> names) {
}
