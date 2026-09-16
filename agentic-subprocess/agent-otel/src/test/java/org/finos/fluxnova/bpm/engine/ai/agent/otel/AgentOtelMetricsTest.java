package org.finos.fluxnova.bpm.engine.ai.agent.otel;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that {@link AgentOtelMetrics} never throws regardless of which optional fields are
 * populated on the direct orchestrator/tool-listener calls it consumes — including against the
 * no-op {@code OpenTelemetry} instance used when no {@code OpenTelemetryProcessEnginePlugin} is
 * configured (the default in these unit tests, since no engine bootstrap happens here).
 */
class AgentOtelMetricsTest {

    private final AgentOtelMetrics metrics = new AgentOtelMetrics();

    @Test
    void recordLlmCall_withTokensAndDuration_doesNotThrow() {
        assertDoesNotThrow(() -> metrics.recordLlmCall("openai", "gpt-4o", 120, 45, 850));
    }

    @Test
    void recordLlmCall_withNoUsageOrDuration_doesNotThrow() {
        assertDoesNotThrow(() -> metrics.recordLlmCall(null, null, 0, 0, 0));
    }

    @Test
    void recordToolCall_completed_doesNotThrow() {
        assertDoesNotThrow(() -> metrics.recordToolCall("exec-1", "Activity_agent", "call-1",
                "Activity_lookup", "lookupCustomer", false, 230));
    }

    @Test
    void recordToolCall_failedWithNoToolName_fallsBackToElementIdAndDoesNotThrow() {
        assertDoesNotThrow(() -> metrics.recordToolCall("exec-1", "Activity_agent", "call-2",
                "Activity_lookup", null, true, 50));
    }

    @Test
    void recordSubprocess_withStartAndEndTime_doesNotThrow() {
        assertDoesNotThrow(() -> metrics.recordSubprocess("exec-1", "Activity_agent",
                "anthropic", "claude-3", 3, Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:05Z"), 500, 150));
    }

    @Test
    void recordSubprocess_withoutStartTime_isNoOp() {
        assertDoesNotThrow(() -> metrics.recordSubprocess("exec-1", "Activity_agent",
                null, null, 0, null, Instant.now(), 0, 0));
    }

    @Test
    void recordSubprocess_afterLlmAndToolCalls_recordsDedicatedAgentMetricsWithoutThrowing() {
        String executionId = "exec-agent-1";

        assertDoesNotThrow(() -> {
            metrics.recordLlmCall("openai", "gpt-4o", 10, 5, 100);
            metrics.recordToolCall(executionId, "Activity_agent", "call-1",
                    "Activity_lookup", "lookupCustomer", false, 50);
            metrics.recordSubprocess(executionId, "Activity_agent", "openai", "gpt-4o", 3,
                    Instant.parse("2024-01-01T00:00:00Z"),
                    Instant.parse("2024-01-01T00:00:01Z"), 0, 0);
        });
    }

    @Test
    void recordSubprocess_withNoRecordedCalls_recordsZeroCountsWithoutThrowing() {
        assertDoesNotThrow(() -> metrics.recordSubprocess("exec-no-calls", "Activity_agent",
                null, null, 1, Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"), 0, 0));
    }
}
