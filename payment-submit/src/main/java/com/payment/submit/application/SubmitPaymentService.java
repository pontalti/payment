package com.payment.submit.application;

import org.springframework.stereotype.Service;

import com.payment.submit.domain.PaymentId;
import com.payment.submit.domain.PaymentInstrument;
import com.payment.submit.domain.SubmitPaymentCommand;
import com.payment.submit.port.in.SubmitPaymentUseCase;
import com.payment.submit.port.out.PaymentEventPublisher;
import com.payment.submit.port.out.PaymentRequestedEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubmitPaymentService implements SubmitPaymentUseCase {

    private final PaymentEventPublisher publisher;

    public SubmitPaymentService(PaymentEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public PaymentId submit(SubmitPaymentCommand command) {
        PaymentId id = PaymentId.newId();

        PaymentInstrument instrument = command.instrument();
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                id.value().toString(),
                command.orderId(),
                command.amount(),
                command.currency(),
                instrument.method().name(),
                instrument.fundingType() == null ? null : instrument.fundingType().name()
        );

        this.publisher.publish(event);

        return id;
    }
}