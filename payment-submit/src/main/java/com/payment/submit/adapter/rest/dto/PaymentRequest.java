package com.payment.submit.adapter.rest.dto;

import java.math.BigDecimal;

import com.payment.submit.adapter.rest.validator.ValidPaymentInstrument;
import com.payment.submit.domain.FundingType;
import com.payment.submit.domain.PaymentInstrument;
import com.payment.submit.domain.PaymentMethod;
import com.payment.submit.domain.SubmitPaymentCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@ValidPaymentInstrument
public record PaymentRequest(
		@NotBlank String orderId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull PaymentMethod method,
        FundingType fundingType
) {
	
    public SubmitPaymentCommand toCommand() {
        return new SubmitPaymentCommand(
                orderId,
                amount,
                currency,
                new PaymentInstrument(method, fundingType)
        );
    }
    
}