package com.company.project.auth.services.impl;

import com.company.project.auth.services.AuthMailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Writes account links to the log instead of emailing them. Anyone who can read the log can use a reset link,
 * so replace this before real users sign up.
 */
@Slf4j
@Service
public class AuthMailServiceImpl implements AuthMailService {

	AuthMailServiceImpl() {
		log.warn("No email service: verification and password reset links are written to this log");
	}

	@Override
	public void sendEmailVerification(String email, String link) {
		log.info("Email verification link for {}: {}", email, link);
	}

	@Override
	public void sendPasswordReset(String email, String link) {
		log.info("Password reset link for {}: {}", email, link);
	}

}
