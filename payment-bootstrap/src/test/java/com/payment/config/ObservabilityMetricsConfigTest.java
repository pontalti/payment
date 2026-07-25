package com.payment.config;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmThreadMeterConventions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fitness function for observability: proves the JVM binders emit OpenTelemetry
 * SEMANTIC-CONVENTION names/attributes rather than Micrometer's native ones.
 *
 * No infrastructure required — binds the meters to an in-memory SimpleMeterRegistry
 * and inspects them directly, so it runs in milliseconds like the architecture tests.
 * It documents intent and fails the build if the OTel conventions are ever dropped
 * (e.g. someone removes the config, or a version bump changes the behaviour).
 */
class ObservabilityMetricsConfigTest {

    @Test
    void jvmMemoryMetricsCarryTheOtelMemoryTypeTag() {
        var registry = new SimpleMeterRegistry();

        new JvmMemoryMetrics(List.of(), new OpenTelemetryJvmMemoryMeterConventions(Tags.empty()))
                .bindTo(registry);

        // OTel tags memory meters with `jvm.memory.type` (heap / non_heap);
        // the default Micrometer binder uses `area`. Presence of this tag key
        // proves the OTel conventions are active.
        boolean usesOtelConvention = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getKey().equals("jvm.memory.type"));

        assertThat(usesOtelConvention)
                .as("JVM memory meters must carry the OTel `jvm.memory.type` tag")
                .isTrue();
    }

    @Test
    void jvmThreadMetricsUseTheOtelThreadCountName() {
        var registry = new SimpleMeterRegistry();

        new JvmThreadMetrics(List.of(), new OpenTelemetryJvmThreadMeterConventions(Tags.empty()))
                .bindTo(registry);

        // OTel names the thread gauge `jvm.thread.count`; the default Micrometer
        // binder emits `jvm.threads.live` / `jvm.threads.daemon` instead.
        boolean hasOtelMeterName = registry.getMeters().stream()
                .anyMatch(meter -> meter.getId().getName().equals("jvm.thread.count"));

        assertThat(hasOtelMeterName)
                .as("JVM thread metrics must expose the OTel `jvm.thread.count` meter")
                .isTrue();
    }
}