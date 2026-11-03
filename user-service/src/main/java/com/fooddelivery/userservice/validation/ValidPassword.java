package com.fooddelivery.userservice.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Password rules in one reusable annotation: 8-72 characters (72 is the BCrypt limit),
 * with at least one letter and one digit.
 */
@NotBlank
@Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidPassword {

    String message() default "invalid password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
