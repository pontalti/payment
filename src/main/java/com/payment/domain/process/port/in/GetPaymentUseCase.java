package com.payment.domain.process.port.in;

import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentPage;

public interface GetPaymentUseCase {
	public Payment getById(PaymentId id);
	public PaymentPage getPage(Long cursor, int limit);
}
