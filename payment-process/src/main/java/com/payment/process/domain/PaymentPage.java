package com.payment.process.domain;

import java.util.List;

public record PaymentPage(
        List<Payment> items,
        Long nextCursor
) {
}