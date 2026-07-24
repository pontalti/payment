package com.payment.process.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.process.domain.Payment;
import com.payment.process.domain.PaymentId;
import com.payment.process.domain.PaymentStatus;
import com.payment.process.domain.ProcessPaymentCommand;
import com.payment.process.port.in.ProcessPaymentUseCase;
import com.payment.process.port.out.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public ProcessPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        Payment payment = Payment.from(command);
        return this.paymentRepository.save(payment);
    }
    
    @Override
    @Transactional
    public Payment updateStatus(PaymentId id, PaymentStatus status) {
        return this.paymentRepository.updateStatus(id, status);
    }
    
    
}