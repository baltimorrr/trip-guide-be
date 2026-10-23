package com.company.project.auth.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * The device a session was opened from, stored with the session. Behind a reverse proxy the address is the proxy's
 * unless {@code server.forward-headers-strategy} is set.
 */
public record ClientInfo(String userAgent, String ip) {

	public static ClientInfo from(HttpServletRequest request) {
		return new ClientInfo(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
	}

}
