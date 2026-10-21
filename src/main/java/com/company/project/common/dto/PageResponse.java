package com.company.project.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of results: {@code {"content": [...], "page": {"size", "number", "totalElements", "totalPages"}}}.
 * Controllers return this instead of a {@link Page}, whose JSON form is not stable.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

	public record PageMetadata(long size, long number, long totalElements, long totalPages) {
	}

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(),
				new PageMetadata(page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages()));
	}

}
