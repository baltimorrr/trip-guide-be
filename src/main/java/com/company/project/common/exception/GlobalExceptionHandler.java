package com.company.project.common.exception;

import java.util.List;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@code application/problem+json} response.
 * <p>
 * The base class already covers Spring MVC's own exceptions (unknown path, bad JSON,
 * wrong method, ...). The handlers below add the application's exceptions. Keep specific
 * handlers here: anything unmatched falls through to the generic 500 handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** One invalid field. Only the field name and message are returned, never the rejected value. */
	public record ValidationError(String field, String message) {
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// A binding failure (e.g. "abc" for a date) has a default message that quotes the value; replace it.
		List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ValidationError(error.getField(),
						error.isBindingFailure() ? "has an invalid value" : error.getDefaultMessage()))
				.toList();
		ProblemDetail problem = validationProblem(errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		List<ValidationError> errors = ex.getConstraintViolations().stream()
				.map(violation -> new ValidationError(violation.getPropertyPath().toString(), violation.getMessage()))
				.toList();
		return validationProblem(errors);
	}

	// Includes subclasses such as ResourceNotFoundException; each carries its own status.
	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException ex) {
		return problem(ex.getStatus(), ex.getMessage());
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Unknown sort property: " + ex.getPropertyName());
	}

	// A database constraint fired (e.g. a unique index hit by a concurrent request).
	// The SQL details stay in the server log.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.debug("Data integrity violation", ex);
		return problem(HttpStatus.CONFLICT, "Request conflicts with existing data");
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Resource was modified concurrently, please retry");
	}

	// Login failures and bad/missing tokens. Login always gets the same message so a caller
	// cannot tell an unknown email from a wrong password or a disabled account.
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthentication(AuthenticationException ex) {
		String detail = switch (ex) {
			case BadCredentialsException e -> "Invalid email or password";
			case AccountStatusException e -> "Invalid email or password";
			case OAuth2AuthenticationException e -> "Invalid or expired access token";
			default -> "Authentication required";
		};
		return problem(HttpStatus.UNAUTHORIZED, detail);
	}

	// Must exist: without it @PreAuthorize denials would fall into the 500 handler below.
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "Access denied");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
	}

	private static ProblemDetail validationProblem(List<ValidationError> errors) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static ProblemDetail problem(HttpStatus status, String detail) {
		return ProblemDetail.forStatusAndDetail(status, detail);
	}

}
