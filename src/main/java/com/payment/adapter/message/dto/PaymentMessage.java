package com.payment.adapter.message.dto;

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