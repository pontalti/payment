package com.payment.submit.port.in;

import com.payment.submit.domain.PaymentId;
import com.payment.submit.domain.SubmitPaymentCommand;

public interface SubmitPaymentUseCase {

    public PaymentId submit(SubmitPaymentCommand command);
}