package com.payment.domain.application.submit;

import org.springframework.stereotype.Service;

import com.payment.domain.submit.SubmitPaymentCommand;
import com.payment.domain.submit.model.PaymentId;
import com.payment.domain.submit.model.PaymentInstrument;
import com.payment.domain.submit.port.in.SubmitPaymentUseCase;
import com.payment.domain.submit.port.out.PaymentEventPublisher;
import com.payment.domain.submit.port.out.PaymentRequestedEvent;

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