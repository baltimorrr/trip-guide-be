package com.company.project.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** An ISO 4217 currency code, any case ("usd", "VND"). Null is valid. */
@Documented
@Constraint(validatedBy = IsoCurrencyValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCurrency {

	String message() default "must be an ISO 4217 currency code";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
