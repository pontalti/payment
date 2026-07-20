package com.payment.domain.process.model;

import java.util.List;

public record PaymentPage(
        List<Payment> items,
        Long nextCursor
) {
}