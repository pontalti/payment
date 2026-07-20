package com.payment.adapter.rest.dto;

import java.time.Instant;

public record PaymentAcceptedResponse(
        String paymentId,
        PaymentStatus status,
        Instant acceptedAt
) {
    public static PaymentAcceptedResponse accepted(String paymentId) {
        return new PaymentAcceptedResponse(paymentId, PaymentStatus.ACCEPTED, Instant.now());
    }
}