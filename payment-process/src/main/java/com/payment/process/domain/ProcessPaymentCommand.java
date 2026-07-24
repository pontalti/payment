package com.payment.process.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record ProcessPaymentCommand(
        PaymentId paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentInstrument instrument
) {
    public ProcessPaymentCommand {
        Objects.requireNonNull(paymentId, "payment uuid must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(instrument, "instrument must not be null");
    }
}