package com.company.project.product.services;

import com.company.project.product.dto.ProductRequest;
import com.company.project.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Product CRUD. Every method taking an id throws ResourceNotFoundException (404) when it doesn't exist. */
public interface ProductService {

	Page<ProductResponse> findAll(Pageable pageable);

	ProductResponse findById(Long id);

	ProductResponse create(ProductRequest request);

	ProductResponse update(Long id, ProductRequest request);

	void delete(Long id);

}
