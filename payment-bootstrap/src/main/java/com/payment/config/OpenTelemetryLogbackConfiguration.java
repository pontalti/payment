package com.payment.config;

import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;

@Configuration
public class OpenTelemetryLogbackConfiguration {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryLogbackConfiguration(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @PostConstruct
    public void configureOpenTelemetryAppender() {
        OpenTelemetryAppender.install(this.openTelemetry);
    }
	
}
