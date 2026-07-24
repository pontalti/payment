package com.payment.submit.adapter.rest;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payment.submit.adapter.rest.dto.PaymentAcceptedResponse;
import com.payment.submit.adapter.rest.dto.PaymentRequest;
import com.payment.submit.domain.PaymentId;
import com.payment.submit.port.in.SubmitPaymentUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final SubmitPaymentUseCase submitPayment;

    public PaymentController(SubmitPaymentUseCase submitPayment) {
        this.submitPayment = submitPayment;
    }

    @PostMapping
    public ResponseEntity<PaymentAcceptedResponse> submit(@Valid @RequestBody PaymentRequest request) {
        PaymentId id = this.submitPayment.submit(request.toCommand());

        return ResponseEntity.accepted() // 202
                .location(URI.create("/api/v1/payments/" + id.value()))
                .body(PaymentAcceptedResponse.accepted(id.value().toString()));
    }
    
}