package com.payment.adapter.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payment.adapter.rest.dto.PaymentPageResponse;
import com.payment.adapter.rest.dto.PaymentResponse;
import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentPage;
import com.payment.domain.process.port.in.GetPaymentUseCase;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentQueryController {

    private final GetPaymentUseCase getPayment;

    public PaymentQueryController(GetPaymentUseCase getPayment) {
        this.getPayment = getPayment;
    }
    
    @GetMapping("/{uuid}")
    public PaymentResponse getByUuid(@PathVariable("uuid") String uuid) {
        Payment payment = this.getPayment.getById(PaymentId.of(uuid));
        return PaymentResponse.from(payment);
    }

    @GetMapping
    public PaymentPageResponse listAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        Long decodedCursor = PaymentPageResponse.decodeCursor(cursor);
        PaymentPage page = this.getPayment.getPage(decodedCursor, limit);
        return PaymentPageResponse.from(page);
    }
}