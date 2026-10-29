package com.company.project.hotel.dto;

import java.util.List;

/** Facilities under one heading (General, Wellness...), alphabetical. */
public record FacilityGroup(String name, List<Facility> facilities) {
}
