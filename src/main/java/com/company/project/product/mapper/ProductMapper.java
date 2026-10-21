package com.company.project.product.mapper;

import com.company.project.product.dto.ProductRequest;
import com.company.project.product.dto.ProductResponse;
import com.company.project.product.model.Product;
import org.springframework.stereotype.Component;

/** Converts between product DTOs and the entity. Field rules (e.g. trimming the name) stay in Product. */
@Component
public class ProductMapper {

	public Product toEntity(ProductRequest request) {
		return Product.create(request.name(), request.description(), request.price());
	}

	public void updateEntity(Product product, ProductRequest request) {
		product.update(request.name(), request.description(), request.price());
	}

	public ProductResponse toResponse(Product product) {
		return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice(),
				product.getCreatedAt(), product.getUpdatedAt());
	}

}
