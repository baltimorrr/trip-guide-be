package com.company.project.product.model;

import java.math.BigDecimal;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Sample entity. State changes go through {@link #update} instead of public setters. */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	public static Product create(String name, String description, BigDecimal price) {
		Product product = new Product();
		product.update(name, description, price);
		return product;
	}

	public void update(String name, String description, BigDecimal price) {
		this.name = name.trim();
		this.description = description;
		this.price = price;
	}

}
