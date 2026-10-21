package com.company.project.user.repository;

import java.util.Optional;

import com.company.project.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/** Callers must pass emails normalized with {@link User#normalizeEmail(String)}. */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
