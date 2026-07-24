package com.payment.domain.process.port.out;

import java.util.Optional;

import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentPage;
import com.payment.domain.process.model.PaymentStatus;

public interface PaymentRepository {

	public Payment save(Payment payment);
    public Optional<Payment> findById(PaymentId id);
    public PaymentPage findNextPage(Long cursor, int limit);
    public Payment updateStatus(PaymentId id, PaymentStatus status);
    public void delete(PaymentId id);
}