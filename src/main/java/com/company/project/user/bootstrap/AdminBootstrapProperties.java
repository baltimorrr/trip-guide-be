package com.company.project.user.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Initial admin account, from APP_BOOTSTRAP_ADMIN_EMAIL / APP_BOOTSTRAP_ADMIN_PASSWORD. Both optional. */
@ConfigurationProperties("app.bootstrap.admin")
public record AdminBootstrapProperties(String email, String password) {

	@Override
	public String toString() {
		return "AdminBootstrapProperties[email=" + email + ", password=***]";
	}

}
