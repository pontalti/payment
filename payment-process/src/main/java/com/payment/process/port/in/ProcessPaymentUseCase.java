package com.payment.process.port.in;


import com.payment.process.domain.Payment;
import com.payment.process.domain.PaymentId;
import com.payment.process.domain.PaymentStatus;
import com.payment.process.domain.ProcessPaymentCommand;

public interface ProcessPaymentUseCase {
	public Payment process(ProcessPaymentCommand command);
	public Payment updateStatus(PaymentId id, PaymentStatus status);
}
