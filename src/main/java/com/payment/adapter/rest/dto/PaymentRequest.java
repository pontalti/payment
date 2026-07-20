package com.payment.adapter.rest.dto;

import java.math.BigDecimal;

import com.payment.adapter.rest.validator.ValidPaymentInstrument;
import com.payment.domain.submit.SubmitPaymentCommand;
import com.payment.domain.submit.model.FundingType;
import com.payment.domain.submit.model.PaymentInstrument;
import com.payment.domain.submit.model.PaymentMethod;

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