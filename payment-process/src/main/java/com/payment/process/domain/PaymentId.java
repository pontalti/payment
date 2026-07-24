package com.payment.process.domain;

import java.util.Objects;
import java.util.UUID;

import xyz.block.uuidv7.MonotonicUUIDv7;

public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PaymentId newId() {
        return new PaymentId(MonotonicUUIDv7.generate());
    }

    public static PaymentId of(String raw) {
        return new PaymentId(UUID.fromString(raw));
    }
}
