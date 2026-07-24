package com.payment.process.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Payment(
        PaymentId id,
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentInstrument instrument,
        PaymentStatus status,
        Instant processedAt
) {
    public Payment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");
    }

    public static Payment from(ProcessPaymentCommand command) {
        return new Payment(
                command.paymentId(),
                command.orderId(),
                command.amount(),
                command.currency(),
                command.instrument(),
                PaymentStatus.PROCESSING,
                Instant.now()
        );
    }
}