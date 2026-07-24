package com.payment.process.domain;

public record PaymentInstrument(PaymentMethod method, FundingType fundingType) {

    public PaymentInstrument {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (method == PaymentMethod.PAYPAL && fundingType != null) {
            throw new IllegalArgumentException("PAYPAL must not carry a funding type");
        }
        if (method != PaymentMethod.PAYPAL && fundingType == null) {
            throw new IllegalArgumentException(method + " requires a funding type");
        }
    }
}