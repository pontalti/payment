package com.payment.adapter.message;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.payment.adapter.message.dto.PaymentMessage;
import com.payment.domain.process.ProcessPaymentCommand;
import com.payment.domain.process.model.FundingType;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentInstrument;
import com.payment.domain.process.model.PaymentMethod;
import com.payment.domain.process.model.PaymentStatus;
import com.payment.domain.process.port.in.ProcessPaymentUseCase;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class PaymentKafkaConsumer {

    private final ProcessPaymentUseCase processPayment;
    private final ObjectMapper objectMapper;

    public PaymentKafkaConsumer(ProcessPaymentUseCase processPayment, ObjectMapper objectMapper) {
        this.processPayment = processPayment;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "${spring.kafka.topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        PaymentMessage message = objectMapper.readValue(payload, PaymentMessage.class);
        ProcessPaymentCommand command = toCommand(message);
        this.processPayment.process(command);
        this.processPayment.updateStatus(command.paymentId(), PaymentStatus.COMPLETED);
    }

    @DltHandler
    public void onDlt(@Payload String payload,
                      @Header(KafkaHeaders.ORIGINAL_TOPIC) String originalTopic,
                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {
        log.error("Payment message sent to DLT. originalTopic={}, error={}, payload={}",originalTopic, errorMessage, payload);
    }

    private ProcessPaymentCommand toCommand(PaymentMessage message) {
        FundingType fundingType = message.fundingType() == null
                ? null
                : FundingType.valueOf(message.fundingType());

        PaymentInstrument instrument = new PaymentInstrument(
                PaymentMethod.valueOf(message.method()),
                fundingType
        );

        return new ProcessPaymentCommand(
                PaymentId.of(message.paymentId()),
                message.orderId(),
                message.amount(),
                message.currency(),
                instrument
        );
    }
}