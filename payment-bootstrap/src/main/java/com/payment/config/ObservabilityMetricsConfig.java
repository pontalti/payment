package com.payment.config;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmClassLoadingMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmCpuMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmThreadMeterConventions;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Re-registers the JVM / process metric binders using Micrometer's OpenTelemetry
 * meter conventions (Micrometer 1.17+, bundled with Spring Boot 4.1).
 *
 * Effect: metrics are emitted with OpenTelemetry SEMANTIC-CONVENTION names and
 * attributes (e.g. jvm.memory.used with the jvm.memory.type attribute) instead of
 * Micrometer's native naming (jvm.memory.used with area/id). That is what makes the
 * prebuilt "JVM Overview (OpenTelemetry)" dashboard in the Grafana LGTM stack light up.
 *
 * No duplicate metrics: Spring Boot's default JVM/process binders are declared
 * @ConditionalOnMissingBean, so defining beans of the SAME TYPE here makes Boot back
 * off and use these OTel-convention versions instead.
 *
 * Placement: this lives in the bootstrap module (composition root), next to
 * PaymentApplication. It is cross-cutting infrastructure config and never touches the
 * bounded contexts or the domain, so the hexagonal / ArchUnit rules are unaffected.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityMetricsConfig {

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics(List.of(), new OpenTelemetryJvmMemoryMeterConventions(Tags.empty()));
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics(List.of(), new OpenTelemetryJvmThreadMeterConventions(Tags.empty()));
    }

    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics(new OpenTelemetryJvmClassLoadingMeterConventions());
    }

    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics(List.of(), new OpenTelemetryJvmCpuMeterConventions(Tags.empty()));
    }
}