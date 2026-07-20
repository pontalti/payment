package com.payment.domain.submit.port.in;

import com.payment.domain.submit.SubmitPaymentCommand;
import com.payment.domain.submit.model.PaymentId;

public interface SubmitPaymentUseCase {

    public PaymentId submit(SubmitPaymentCommand command);
}