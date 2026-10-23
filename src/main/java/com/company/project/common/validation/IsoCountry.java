package com.company.project.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** An ISO 3166 alpha-2 country code, any case ("vn", "VN"). Null is valid. */
@Documented
@Constraint(validatedBy = IsoCountryValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCountry {

	String message() default "must be an ISO 3166 two-letter country code";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
