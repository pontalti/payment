package com.payment.domain.process.port.in;

import com.payment.domain.process.ProcessPaymentCommand;

public interface PaymentEventConsumer {

	public void process(ProcessPaymentCommand command);
	
}
