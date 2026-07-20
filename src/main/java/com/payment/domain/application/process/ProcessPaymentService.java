package com.payment.domain.application.process;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.domain.process.ProcessPaymentCommand;
import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentStatus;
import com.payment.domain.process.port.in.ProcessPaymentUseCase;
import com.payment.domain.process.port.out.PaymentRepository;

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