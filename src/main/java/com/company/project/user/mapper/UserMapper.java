package com.company.project.user.mapper;

import com.company.project.user.dto.UserResponse;
import com.company.project.user.model.User;
import org.springframework.stereotype.Component;

/** Converts User entities to API responses, so entities never leave the service layer. */
@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
				user.getCountryCode(), user.getPreferredCurrency(), user.isEmailVerified(),
				user.getRoles().stream().sorted().toList(), user.getCreatedAt());
	}

}
