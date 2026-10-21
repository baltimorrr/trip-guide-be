package com.company.project.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(Long id, String name, String description, BigDecimal price, Instant createdAt,
		Instant updatedAt) {
}
