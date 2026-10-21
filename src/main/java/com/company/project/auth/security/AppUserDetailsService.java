package com.company.project.auth.security;

import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads users for password login. The "username" is the email address. */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		User user = users.findByEmail(User.normalizeEmail(username))
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
		// Fully qualified: Spring Security's User, not our entity.
		return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.roles(user.getRoles().stream().map(Role::name).toArray(String[]::new))
				.disabled(!user.isEnabled())
				.build();
	}

}
