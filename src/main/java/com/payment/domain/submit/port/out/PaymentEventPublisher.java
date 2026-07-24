package com.payment.domain.submit.port.out;

public interface PaymentEventPublisher {

    public void publish(PaymentRequestedEvent event);
}