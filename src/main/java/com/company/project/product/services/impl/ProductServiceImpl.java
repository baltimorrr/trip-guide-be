package com.company.project.product.services.impl;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.product.dto.ProductRequest;
import com.company.project.product.dto.ProductResponse;
import com.company.project.product.mapper.ProductMapper;
import com.company.project.product.model.Product;
import com.company.project.product.repository.ProductRepository;
import com.company.project.product.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only transactions by default; methods that write opt in with {@code @Transactional}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

	private final ProductRepository products;

	private final ProductMapper productMapper;

	@Override
	public Page<ProductResponse> findAll(Pageable pageable) {
		return products.findAll(pageable).map(productMapper::toResponse);
	}

	@Override
	public ProductResponse findById(Long id) {
		return productMapper.toResponse(load(id));
	}

	@Override
	@Transactional
	public ProductResponse create(ProductRequest request) {
		Product product = productMapper.toEntity(request);
		// Flush now so the generated id, version and timestamps are in the response.
		return productMapper.toResponse(products.saveAndFlush(product));
	}

	@Override
	@Transactional
	public ProductResponse update(Long id, ProductRequest request) {
		Product product = load(id);
		productMapper.updateEntity(product, request);
		return productMapper.toResponse(products.saveAndFlush(product));
	}

	@Override
	@Transactional
	public void delete(Long id) {
		products.delete(load(id));
	}

	private Product load(Long id) {
		return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
	}

}
