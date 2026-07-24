package com.payment.process.port.out;

import java.util.Optional;

import com.payment.process.domain.Payment;
import com.payment.process.domain.PaymentId;
import com.payment.process.domain.PaymentPage;
import com.payment.process.domain.PaymentStatus;

public interface PaymentRepository {

	public Payment save(Payment payment);
    public Optional<Payment> findById(PaymentId id);
    public PaymentPage findNextPage(Long cursor, int limit);
    public Payment updateStatus(PaymentId id, PaymentStatus status);
    public void delete(PaymentId id);
}