package com.payment.domain.process.port.in;


import com.payment.domain.process.ProcessPaymentCommand;
import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentStatus;

public interface ProcessPaymentUseCase {
	public Payment process(ProcessPaymentCommand command);
	public Payment updateStatus(PaymentId id, PaymentStatus status);
}
