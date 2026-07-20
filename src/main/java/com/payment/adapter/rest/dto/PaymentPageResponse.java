package com.payment.adapter.rest.dto;

import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;

import com.payment.domain.process.model.PaymentPage;

public record PaymentPageResponse(
        List<PaymentResponse> items,
        String nextCursor   // Base64 opaco, ou null
) {
    public static PaymentPageResponse from(PaymentPage page) {
        List<PaymentResponse> items = page.items().stream()
                .map(PaymentResponse::from)
                .toList();

        String cursor = (page.nextCursor() == null)
                ? null
                : Base64.getUrlEncoder().encodeToString(
                        page.nextCursor().toString().getBytes(StandardCharsets.UTF_8));

        return new PaymentPageResponse(items, cursor);
    }

    public static Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        return Long.valueOf(decoded);
    }
}