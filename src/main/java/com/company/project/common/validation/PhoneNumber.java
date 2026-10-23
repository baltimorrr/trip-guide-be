package com.company.project.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A phone number as typed: optional +, digits, spaces, dots, dashes and brackets, at most 30 characters. Null is valid. */
@Documented
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Size(max = 30)
@Pattern(regexp = "^\\s*\\+?[0-9][0-9 ().-]*$")
public @interface PhoneNumber {

	String message() default "must be a phone number of at most 30 characters";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
