package com.company.project.user.services;

import com.company.project.user.dto.ProfileUpdateRequest;
import com.company.project.user.dto.UserResponse;

/** The signed-in user's own account details. Both throw ResourceNotFoundException (404) for a deleted account. */
public interface ProfileService {

	UserResponse get(Long userId);

	UserResponse update(Long userId, ProfileUpdateRequest request);

}
