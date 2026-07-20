package com.payment.domain.process.model;

public class PaymentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(PaymentId id) {
        super("Payment not found: " + id.value());
    }

}
