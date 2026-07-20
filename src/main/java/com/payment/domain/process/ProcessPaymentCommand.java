package com.payment.domain.process;

import java.math.BigDecimal;
import java.util.Objects;

import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentInstrument;

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