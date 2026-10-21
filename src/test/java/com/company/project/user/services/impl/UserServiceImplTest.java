package com.company.project.user.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Plain unit tests: repository and password encoder are mocks, no Spring context or database. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

	@Mock
	UserRepository users;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	UserServiceImpl userService;

	@Test
	void passwordOverByteLimitIsRejectedBeforeAnyLookup() {
		String password = "é".repeat(37); // 37 characters, 74 bytes in UTF-8

		assertThatThrownBy(() -> userService.create("jane@example.com", password, "Jane", Set.of(Role.USER)))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verifyNoInteractions(users, passwordEncoder);
	}

	@Test
	void existingEmailIsConflictRegardlessOfCase() {
		when(users.existsByEmail("jane@example.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.create(" Jane@Example.com ", "jane-password-1", "Jane", Set.of(Role.USER)))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void createdUserIsNormalizedAndStoresOnlyTheHash() {
		when(passwordEncoder.encode("jane-password-1")).thenReturn("{bcrypt}hash");
		when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User user = userService.create(" Jane@Example.com ", "jane-password-1", "  Jane Doe ", Set.of(Role.USER));

		assertThat(user.getEmail()).isEqualTo("jane@example.com");
		assertThat(user.getFullName()).isEqualTo("Jane Doe");
		assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}hash");
		assertThat(user.getRoles()).containsExactly(Role.USER);
		assertThat(user.isEnabled()).isTrue();
	}

}
