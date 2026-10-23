package com.company.project.user.services.impl;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.user.dto.ProfileUpdateRequest;
import com.company.project.user.dto.UserResponse;
import com.company.project.user.mapper.UserMapper;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileServiceImpl implements ProfileService {

	private final UserRepository users;

	private final UserMapper userMapper;

	@Override
	public UserResponse get(Long userId) {
		return userMapper.toResponse(find(userId));
	}

	@Override
	@Transactional
	public UserResponse update(Long userId, ProfileUpdateRequest request) {
		User user = find(userId);
		user.updateProfile(request.fullName(), request.phone(), request.countryCode(), request.preferredCurrency());
		return userMapper.toResponse(users.saveAndFlush(user));
	}

	private User find(Long userId) {
		return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
	}

}
