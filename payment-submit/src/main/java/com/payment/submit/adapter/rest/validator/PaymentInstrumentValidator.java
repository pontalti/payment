package com.payment.submit.adapter.rest.validator;

import com.payment.submit.adapter.rest.dto.PaymentRequest;
import com.payment.submit.domain.PaymentMethod;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaymentInstrumentValidator implements ConstraintValidator<ValidPaymentInstrument, PaymentRequest> {

    @Override
    public boolean isValid(PaymentRequest req, ConstraintValidatorContext ctx) {
        if (req == null || req.method() == null) {
            return true; // @NotNull no 'method' cuida do null
        }
        boolean isPaypal = req.method() == PaymentMethod.PAYPAL;
        boolean hasFunding = req.fundingType() != null;

        if (isPaypal && hasFunding) {
            return violation(ctx, "must be absent for PAYPAL");
        }
        if (!isPaypal && !hasFunding) {
            return violation(ctx, "is required for " + req.method());
        }
        return true;
    }

    private boolean violation(ConstraintValidatorContext ctx, String message) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message)
           .addPropertyNode("fundingType")
           .addConstraintViolation();
        return false;
    }
}