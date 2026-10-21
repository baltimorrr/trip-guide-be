package com.company.project.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.company.project.common.exception.BusinessException;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;

/** Plain unit tests: which failures while creating the admin are tolerated at startup. */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

	@Mock
	UserRepository users;

	@Mock
	UserService userService;

	@Test
	void adminCreatedConcurrentlyIsSkipped() {
		when(userService.create(anyString(), anyString(), anyString(), any()))
				.thenThrow(new BusinessException(HttpStatus.CONFLICT, "Email already registered"));

		assertThatCode(this::runBootstrap).doesNotThrowAnyException();
	}

	@Test
	void otherBusinessErrorsStillFailStartup() {
		when(userService.create(anyString(), anyString(), anyString(), any()))
				.thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "Password must be at most 72 bytes"));

		assertThatThrownBy(this::runBootstrap).isInstanceOf(BusinessException.class).hasMessageContaining("72 bytes");
	}

	private void runBootstrap() {
		AdminBootstrapProperties properties = new AdminBootstrapProperties("admin@example.com", "admin-password-123");
		new AdminBootstrap(properties, users, userService).run(new DefaultApplicationArguments());
	}

}
