package com.company.project.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The password rule for every new password: 8 to 72 characters with at least one letter and one digit. Each part
 * reports its own message. Null is valid; combine with {@code @NotBlank}. The 72-byte BCrypt limit is checked
 * separately ({@code UserService.exceedsPasswordByteLimit}).
 */
@Documented
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "must contain at least one letter and one digit")
public @interface StrongPassword {

	String message() default "is not a strong password";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
