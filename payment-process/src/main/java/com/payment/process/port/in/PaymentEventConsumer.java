package com.payment.process.port.in;

import com.payment.process.domain.ProcessPaymentCommand;

public interface PaymentEventConsumer {

	public void process(ProcessPaymentCommand command);
	
}
