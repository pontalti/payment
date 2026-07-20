package com.payment.domain.submit;

import java.math.BigDecimal;
import java.util.Objects;

import com.payment.domain.submit.model.PaymentInstrument;

public record SubmitPaymentCommand(
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentInstrument instrument
) {
    public SubmitPaymentCommand {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(instrument, "instrument must not be null");
    }
}