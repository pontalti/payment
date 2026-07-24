package com.payment.submit.adapter.kafka;

public class PaymentEventPublishingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

	public PaymentEventPublishingException(String paymentId, Throwable cause) {
        super("Failed to publish payment event for id=" + paymentId, cause);
    }
}