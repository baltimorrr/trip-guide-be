package com.company.project.user.dto;

import java.time.Instant;
import java.util.List;

import com.company.project.user.model.Role;

public record UserResponse(Long id, String email, String fullName, List<Role> roles, Instant createdAt) {
}
