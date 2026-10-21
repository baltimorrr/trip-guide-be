package com.company.project.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A request the application refuses (invalid input Bean Validation cannot express, a conflict
 * with existing state, ...). Rendered by GlobalExceptionHandler with the given status.
 */
public class BusinessException extends RuntimeException {

	private final HttpStatus status;

	public BusinessException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
