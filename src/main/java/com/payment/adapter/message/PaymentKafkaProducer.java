package com.payment.adapter.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.payment.domain.submit.port.out.PaymentEventPublisher;
import com.payment.domain.submit.port.out.PaymentRequestedEvent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class PaymentKafkaProducer implements PaymentEventPublisher {

    @Value("${spring.kafka.topic.name}")
    private String topic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentKafkaProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PaymentRequestedEvent event) {
        String payload = this.objectMapper.writeValueAsString(event);
        this.kafkaTemplate.send(topic, event.paymentId(), payload);
    }
}