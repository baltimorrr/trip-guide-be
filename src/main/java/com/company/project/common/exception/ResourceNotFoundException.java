package com.company.project.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a requested resource does not exist; rendered as 404. */
public class ResourceNotFoundException extends BusinessException {

	public ResourceNotFoundException(String resource, Object id) {
		super(HttpStatus.NOT_FOUND, "%s %s not found".formatted(resource, id));
	}

}
