package com.payment.submit.port.out;

public interface PaymentEventPublisher {

    public void publish(PaymentRequestedEvent event);
}