package com.company.project.user.services.impl;

import java.util.Set;

import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public User create(String email, String rawPassword, String fullName, Set<Role> roles) {
		if (UserService.exceedsPasswordByteLimit(rawPassword)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST,
					"Password must be at most %d bytes".formatted(MAX_PASSWORD_BYTES));
		}
		String normalizedEmail = User.normalizeEmail(email);
		if (users.existsByEmail(normalizedEmail)) {
			throw new BusinessException(HttpStatus.CONFLICT, "Email already registered");
		}
		// A concurrent registration with the same email still fails on the unique
		// constraint, which GlobalExceptionHandler maps to 409.
		User user = User.create(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim(), roles);
		return users.saveAndFlush(user);
	}

}
