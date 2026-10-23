package com.company.project.auth.mail;

/** Sends the account emails. Only {@link LoggingAuthMailer} exists until an email service is chosen. */
public interface AuthMailer {

	void sendEmailVerification(String email, String link);

	void sendPasswordReset(String email, String link);

}
