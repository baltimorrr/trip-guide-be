package com.company.project.product.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.product.dto.ProductRequest;
import com.company.project.product.dto.ProductResponse;
import com.company.project.product.mapper.ProductMapper;
import com.company.project.product.model.Product;
import com.company.project.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Plain unit tests: the repository is a mock, the mapper is the real one. */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

	static final ProductRequest REQUEST = new ProductRequest("  Coffee beans ", "1kg bag", new BigDecimal("19.99"));

	@Mock
	ProductRepository products;

	ProductServiceImpl productService;

	@BeforeEach
	void setUp() {
		productService = new ProductServiceImpl(products, new ProductMapper());
	}

	@Test
	void findingMissingProductIsNotFound() {
		when(products.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productService.findById(42L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Product 42 not found");
	}

	@Test
	void createSavesMappedEntityAndReturnsIt() {
		when(products.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProductResponse created = productService.create(REQUEST);

		assertThat(created.name()).isEqualTo("Coffee beans");
		assertThat(created.description()).isEqualTo("1kg bag");
		assertThat(created.price()).isEqualByComparingTo("19.99");
	}

	@Test
	void updateAppliesRequestToLoadedProduct() {
		Product existing = Product.create("Old name", null, BigDecimal.ONE);
		when(products.findById(7L)).thenReturn(Optional.of(existing));
		when(products.saveAndFlush(existing)).thenReturn(existing);

		ProductResponse updated = productService.update(7L, REQUEST);

		assertThat(existing.getName()).isEqualTo("Coffee beans");
		assertThat(updated.price()).isEqualByComparingTo("19.99");
	}

	@Test
	void deletingMissingProductIsNotFoundAndDeletesNothing() {
		when(products.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productService.delete(42L)).isInstanceOf(ResourceNotFoundException.class);
		verify(products, never()).delete(any());
	}

}
