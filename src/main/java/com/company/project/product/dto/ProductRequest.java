package com.company.project.product.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for create and update. Limits mirror the products table columns. */
public record ProductRequest(
		@Schema(example = "Coffee beans") @NotBlank @Size(max = 120) String name,
		@Schema(example = "1kg bag") @Size(max = 1000) String description,
		@Schema(example = "19.99") @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price) {
}
