package com.payment.process.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.process.domain.Payment;
import com.payment.process.domain.PaymentId;
import com.payment.process.domain.PaymentNotFoundException;
import com.payment.process.domain.PaymentPage;
import com.payment.process.port.in.GetPaymentUseCase;
import com.payment.process.port.out.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GetPaymentService implements GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Payment getById(PaymentId id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public PaymentPage getPage(Long cursor, int limit) {
        return paymentRepository.findNextPage(cursor, limit);
    }

}
