package com.company.project.common.exception;

import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ErrorResponseTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void unknownPathWithoutTokenIsUnauthorized() {
		// Every non-public path requires authentication, so this never reveals which paths exist.
		MvcTestResult result = mvc.get().uri("/does-not-exist").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
	}

	@Test
	void unknownPathWithTokenReturnsNotFoundProblem() {
		MvcTestResult result = mvc.get().uri("/does-not-exist")
				.header(HttpHeaders.AUTHORIZATION, bearer(userToken(mvc))).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
	}

	@Test
	void openApiSpecIsPublic() {
		assertThat(mvc.get().uri("/v3/api-docs"))
				.hasStatusOk()
				.bodyJson()
				.hasPath("$.openapi");
	}

}
