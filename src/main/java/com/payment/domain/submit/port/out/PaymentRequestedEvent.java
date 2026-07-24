package com.payment.domain.submit.port.out;

import java.math.BigDecimal;

public record PaymentRequestedEvent(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String method,        // enum as String — consumer-neutral
        String fundingType    // can be null (PayPal)
) {
}