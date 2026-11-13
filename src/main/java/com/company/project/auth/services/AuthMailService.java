package com.company.project.auth.services;

/** Sends the account emails. The only implementation writes the links to the log until an email service is chosen. */
public interface AuthMailService {

	void sendEmailVerification(String email, String link);

	void sendPasswordReset(String email, String link);

}
