package com.company.project.user.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.user.dto.ProfileUpdateRequest;
import com.company.project.user.dto.UserResponse;
import com.company.project.user.mapper.UserMapper;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

	@Mock
	UserRepository users;

	@Spy
	UserMapper userMapper = new UserMapper();

	@InjectMocks
	ProfileServiceImpl service;

	@Test
	void missingUserIsNotFound() {
		when(users.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.update(42L, new ProfileUpdateRequest("Jane", null, null, null)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateNormalizesThroughTheEntity() {
		User user = User.create("jane@example.com", "{noop}x", "Jane", "0901", Set.of(Role.USER), Instant.now());
		when(users.findById(42L)).thenReturn(Optional.of(user));
		when(users.saveAndFlush(user)).thenReturn(user);

		UserResponse response = service.update(42L, new ProfileUpdateRequest(" Jane Doe ", "  ", " vn ", "eur"));

		assertThat(response.fullName()).isEqualTo("Jane Doe");
		assertThat(response.phone()).isNull();
		assertThat(response.countryCode()).isEqualTo("VN");
		assertThat(response.preferredCurrency()).isEqualTo("EUR");
		assertThat(response.emailVerified()).isTrue();
	}

}
