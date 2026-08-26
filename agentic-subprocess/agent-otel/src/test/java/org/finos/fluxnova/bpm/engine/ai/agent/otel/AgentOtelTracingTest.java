package org.finos.fluxnova.bpm.engine.ai.agent.otel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies {@link AgentOtelTracing} span start/end correlation, including the case where an
 * "end" call arrives without a matching "start" (e.g. the process restarted mid-flight) —
 * which must be silently ignored rather than throw. Runs against the default no-op {@code
 * OpenTelemetry} instance, since no {@code OpenTelemetryProcessEnginePlugin} is configured in
 * these unit tests.
 */
class AgentOtelTracingTest {

    private final AgentOtelTracing tracing = new AgentOtelTracing();

    private final AgentOtelContentCaptureProperties captureEnabled = captureEnabledProperties();

    @Test
    void subprocessSpan_startThenEnd_doesNotThrow() {
        assertDoesNotThrow(() -> {
            tracing.startSubprocess("exec-1", "Activity_agent", "proc-1", "openai", "gpt-4o",
                    "goal", "{\"customerId\":\"C123\"}");
            tracing.endSubprocess("exec-1", 10, 5, 1, 2, "done");
        });
    }

    @Test
    void subprocessSpan_endWithoutStart_isNoOp() {
        assertDoesNotThrow(() -> tracing.endSubprocess("exec-orphan", 0, 0, 0, 0, null));
    }

    @Test
    void llmSpan_startThenEnd_correlatesByLoopIndex() {
        assertDoesNotThrow(() -> {
            tracing.startLlmCall("exec-1", 1, "openai", "gpt-4o",
                    "[{\"role\":\"user\",\"content\":\"hello\"}]");
            tracing.endLlmCall("exec-1", 1, "openai", "gpt-4o", 20, 8, "hi");
        });
    }

    @Test
    void llmSpan_endWithoutStart_isNoOp() {
        assertDoesNotThrow(() -> tracing.endLlmCall("exec-orphan", 99, null, null, 0, 0, null));
    }

    @Test
    void toolCallSpan_startThenFailedEnd_doesNotThrow() {
        assertDoesNotThrow(() -> {
            tracing.startToolCall("exec-1", "Activity_agent", "call-1", "Activity_lookup",
                    null, "{\"city\":\"London\"}");
            tracing.endToolCall("call-1", "Activity_lookup", "lookupCustomer", true, "timeout",
                    null);
        });
    }

    @Test
    void toolCallSpan_endWithoutStart_isNoOp() {
        assertDoesNotThrow(
                () -> tracing.endToolCall("call-orphan", "Activity_lookup", null, false, null, null));
    }

    @Test
    void contentCapture_disabledByDefault_doesNotThrowWithContentPopulated() {
        assertDoesNotThrow(() -> {
            tracing.startSubprocess("exec-content-1", "Activity_agent", "proc-1", "openai",
                    "gpt-4o", "Book a flight for the customer", "{\"customerId\":\"C123\"}");
            tracing.startLlmCall("exec-content-1", 1, "openai", "gpt-4o",
                    "[{\"role\":\"user\",\"content\":\"hello\"}]");
            tracing.endLlmCall("exec-content-1", 1, "openai", "gpt-4o", 0, 0, "hi there");
            tracing.startToolCall("exec-content-1", "Activity_agent", "call-content-1",
                    "Activity_lookup", null, "{\"city\":\"London\"}");
            tracing.endToolCall("call-content-1", "Activity_lookup", null, false, null,
                    "{\"flightId\":\"BA123\"}");
            tracing.endSubprocess("exec-content-1", 0, 0, 1, 1, "Booked flight BA123");
        });
    }

    @Test
    void contentCapture_enabled_doesNotThrowWithContentPopulated() {
        AgentOtelTracing captureTracing = new AgentOtelTracing(captureEnabled);

        assertDoesNotThrow(() -> {
            captureTracing.startSubprocess("exec-content-2", "Activity_agent", "proc-2",
                    "openai", "gpt-4o", "Book a flight for the customer",
                    "{\"customerId\":\"C123\"}");
            captureTracing.startLlmCall("exec-content-2", 1, "openai", "gpt-4o",
                    "[{\"role\":\"user\",\"content\":\"hello\"}]");
            captureTracing.endLlmCall("exec-content-2", 1, "openai", "gpt-4o", 0, 0, "hi there");
            captureTracing.startToolCall("exec-content-2", "Activity_agent", "call-content-2",
                    "Activity_lookup", null, "{\"city\":\"London\"}");
            captureTracing.endToolCall("call-content-2", "Activity_lookup", null, false, null,
                    "{\"flightId\":\"BA123\"}");
            captureTracing.endSubprocess("exec-content-2", 0, 0, 1, 1, "Booked flight BA123");
        });
    }

    private static AgentOtelContentCaptureProperties captureEnabledProperties() {
        AgentOtelContentCaptureProperties properties = new AgentOtelContentCaptureProperties();
        properties.setCaptureContent(true);
        return properties;
    }
}
