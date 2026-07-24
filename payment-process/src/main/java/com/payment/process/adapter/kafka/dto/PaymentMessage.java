package com.payment.process.adapter.kafka.dto;

import java.math.BigDecimal;

public record PaymentMessage(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String method,
        String fundingType
) {
}