package com.company.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class ProjectApplicationTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void healthEndpointReportsUp() {
		assertThat(mvc.get().uri("/actuator/health"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.status")
				.isEqualTo("UP");
	}

}
