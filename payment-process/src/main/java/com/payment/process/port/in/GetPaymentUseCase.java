package com.payment.process.port.in;

import com.payment.process.domain.Payment;
import com.payment.process.domain.PaymentId;
import com.payment.process.domain.PaymentPage;

public interface GetPaymentUseCase {
	public Payment getById(PaymentId id);
	public PaymentPage getPage(Long cursor, int limit);
}
