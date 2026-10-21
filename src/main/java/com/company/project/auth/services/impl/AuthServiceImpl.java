package com.company.project.auth.services.impl;

import java.util.Set;

import com.company.project.auth.dto.LoginRequest;
import com.company.project.auth.dto.RegisterRequest;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.auth.security.TokenService;
import com.company.project.auth.services.AuthService;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.user.dto.UserResponse;
import com.company.project.user.mapper.UserMapper;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private final UserService userService;

	private final UserRepository users;

	private final AuthenticationManager authenticationManager;

	private final TokenService tokenService;

	private final UserMapper userMapper;

	@Override
	public UserResponse register(RegisterRequest request) {
		User user = userService.create(request.email(), request.password(), request.fullName(), Set.of(Role.USER));
		return userMapper.toResponse(user);
	}

	@Override
	public TokenResponse login(LoginRequest request) {
		String email = User.normalizeEmail(request.email());
		// No stored password can be longer, and BCrypt would reject it rather than answer "no match".
		if (UserService.exceedsPasswordByteLimit(request.password())) {
			throw new BadCredentialsException("Bad credentials");
		}
		authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
		User user = users.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Bad credentials"));
		return tokenService.issue(user);
	}

	/** The token subject is the user id; reload so a deleted account is not served from a stale token. */
	@Override
	@Transactional(readOnly = true)
	public UserResponse currentUser(Jwt jwt) {
		return users.findById(Long.valueOf(jwt.getSubject()))
				.map(userMapper::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("User", jwt.getSubject()));
	}

}
