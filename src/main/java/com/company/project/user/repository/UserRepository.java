package com.company.project.user.repository;

import java.util.Optional;

import com.company.project.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Callers must pass emails normalized with {@link User#normalizeEmail(String)}. The sign-in lockout updates are
 * declared in {@link UserRepositoryCustom} and implemented in {@code impl/UserRepositoryImpl}.
 */
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
