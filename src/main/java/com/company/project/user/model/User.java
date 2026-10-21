package com.company.project.user.model;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An application user. Deliberately no Lombok {@code @Data}/{@code @ToString}: a generated
 * toString would print the password hash, and generated equals/hashCode break JPA identity.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(nullable = false)
	private boolean enabled = true;

	// Roles are always needed to build the security principal, so load them with the user.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = new HashSet<>();

	public static User create(String email, String passwordHash, String fullName, Set<Role> roles) {
		User user = new User();
		user.email = normalizeEmail(email);
		user.passwordHash = passwordHash;
		user.fullName = fullName;
		user.roles = new HashSet<>(roles);
		return user;
	}

	/** Emails are compared case-insensitively by storing them trimmed and lowercased. */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
