package com.payment.process.adapter.persistence.config;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
* Keeps the domain free of annotations. Jackson: PaymentId is recorded in Redis
* as a pure string ("6534e709-...") instead of {"value":"6534e709-..."}.
*/
abstract class PaymentIdMixin {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    PaymentIdMixin(UUID value) {
    }

    @JsonValue
    abstract UUID value();
}
