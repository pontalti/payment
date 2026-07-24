package com.payment.submit.adapter.rest.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PaymentInstrumentValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPaymentInstrument {

    String message() default "invalid payment instrument";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}