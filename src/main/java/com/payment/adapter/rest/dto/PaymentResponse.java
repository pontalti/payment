package com.payment.adapter.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.payment.domain.process.model.Payment;

public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String method,
        String fundingType,
        String status,
        Instant processedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.id().value().toString(),
                payment.orderId(),
                payment.amount(),
                payment.currency(),
                payment.instrument().method().name(),
                payment.instrument().fundingType() == null ? null : payment.instrument().fundingType().name(),
                payment.status().name(),
                payment.processedAt()
        );
    }
}