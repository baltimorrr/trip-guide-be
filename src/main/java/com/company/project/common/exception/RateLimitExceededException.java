package com.company.project.common.exception;

/**
 * A client went over one of its rate limits. Rendered by GlobalExceptionHandler as 429 with {@code Retry-After}, so it
 * is not a {@link BusinessException}: that handler sets no headers.
 */
public class RateLimitExceededException extends RuntimeException {

	/** Whole seconds until the client may send one more request. */
	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super("Rate limit exceeded");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
