package com.company.project.user.bootstrap;

import java.util.EnumSet;

import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first admin on startup when credentials are configured. Never changes an
 * existing account, so rotating the configured password does not reset a live admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AdminBootstrap implements ApplicationRunner {

	static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

	private final AdminBootstrapProperties properties;

	private final UserRepository users;

	private final UserService userService;

	@Override
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
			log.info("Admin bootstrap skipped: APP_BOOTSTRAP_ADMIN_EMAIL / APP_BOOTSTRAP_ADMIN_PASSWORD not set");
			return;
		}
		String email = User.normalizeEmail(properties.email());
		if (users.existsByEmail(email)) {
			log.info("Admin bootstrap skipped: {} already exists", email);
			return;
		}
		if (properties.password().length() < MIN_ADMIN_PASSWORD_LENGTH) {
			throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD must be at least %d characters"
					.formatted(MIN_ADMIN_PASSWORD_LENGTH));
		}
		try {
			userService.create(email, properties.password(), "Administrator", EnumSet.of(Role.ADMIN, Role.USER));
			log.info("Admin bootstrap created {}", email);
		}
		catch (BusinessException | DataIntegrityViolationException ex) {
			// Only a conflict is expected here; anything else (e.g. a password over the
			// 72-byte limit) is a misconfiguration and must still fail startup.
			if (ex instanceof BusinessException business && business.getStatus() != HttpStatus.CONFLICT) {
				throw business;
			}
			// Another instance created it between the check and the insert.
			log.info("Admin bootstrap skipped: {} was created concurrently", email);
		}
	}

}
