package org.finos.fluxnova.bpm.engine.ai.agent.history.otel;

import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that {@link AgentOtelMetrics} never throws regardless of which optional fields are
 * populated on the history events it consumes — including against the no-op {@code OpenTelemetry}
 * instance used when no {@code OpenTelemetryProcessEnginePlugin} is configured (the default in
 * these unit tests, since no engine bootstrap happens here).
 */
class AgentOtelMetricsTest {

    private final AgentOtelMetrics metrics = new AgentOtelMetrics();

    @Test
    void recordLlmCall_withTokensAndDuration_doesNotThrow() {
        AgentLlmHistoryEvent event = new AgentLlmHistoryEvent();
        event.setProvider("openai");
        event.setModel("gpt-4o");
        event.setPromptTokens(120);
        event.setCompletionTokens(45);
        event.setDurationMs(850);

        assertDoesNotThrow(() -> metrics.recordLlmCall(event));
    }

    @Test
    void recordLlmCall_withNoUsageOrDuration_doesNotThrow() {
        AgentLlmHistoryEvent event = new AgentLlmHistoryEvent();

        assertDoesNotThrow(() -> metrics.recordLlmCall(event));
    }

    @Test
    void recordToolCall_completed_doesNotThrow() {
        AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
        event.setToolCallId("call-1");
        event.setToolName("lookupCustomer");
        event.setToolElementId("Activity_lookup");
        event.setStatus("COMPLETED");
        event.setDurationMs(230);

        assertDoesNotThrow(() -> metrics.recordToolCall(event));
    }

    @Test
    void recordToolCall_failedWithNoToolName_fallsBackToElementIdAndDoesNotThrow() {
        AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
        event.setToolCallId("call-2");
        event.setToolElementId("Activity_lookup");
        event.setStatus("FAILED");
        event.setErrorMessage("boom");
        event.setDurationMs(50);

        assertDoesNotThrow(() -> metrics.recordToolCall(event));
    }

    @Test
    void recordSubprocess_withStartAndEndTime_doesNotThrow() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setProvider("anthropic");
        event.setModel("claude-3");
        event.setStartTime(Instant.parse("2024-01-01T00:00:00Z"));
        event.setEndTime(Instant.parse("2024-01-01T00:00:05Z"));
        event.setTotalPromptTokens(500);
        event.setTotalCompletionTokens(150);

        assertDoesNotThrow(() -> metrics.recordSubprocess(event));
    }

    @Test
    void recordSubprocess_withoutStartTime_isNoOp() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setEndTime(Instant.now());

        assertDoesNotThrow(() -> metrics.recordSubprocess(event));
    }

    @Test
    void recordSubprocess_afterLlmAndToolCalls_recordsDedicatedAgentMetricsWithoutThrowing() {
        String executionId = "exec-agent-1";

        AgentLlmHistoryEvent llmEvent = new AgentLlmHistoryEvent();
        llmEvent.setSubprocessExecutionId(executionId);
        llmEvent.setProvider("openai");
        llmEvent.setModel("gpt-4o");
        llmEvent.setPromptTokens(10);
        llmEvent.setCompletionTokens(5);
        llmEvent.setDurationMs(100);

        AgentToolCallHistoryEvent toolEvent = new AgentToolCallHistoryEvent();
        toolEvent.setSubprocessExecutionId(executionId);
        toolEvent.setSubprocessElementId("Activity_agent");
        toolEvent.setToolCallId("call-1");
        toolEvent.setToolName("lookupCustomer");
        toolEvent.setStatus("COMPLETED");
        toolEvent.setDurationMs(50);

        AgentSubprocessHistoryEvent subprocessEvent = new AgentSubprocessHistoryEvent();
        subprocessEvent.setSubprocessExecutionId(executionId);
        subprocessEvent.setSubprocessElementId("Activity_agent");
        subprocessEvent.setProvider("openai");
        subprocessEvent.setModel("gpt-4o");
        subprocessEvent.setStartTime(Instant.parse("2024-01-01T00:00:00Z"));
        subprocessEvent.setEndTime(Instant.parse("2024-01-01T00:00:01Z"));

        assertDoesNotThrow(() -> {
            metrics.recordLlmCall(llmEvent);
            metrics.recordToolCall(toolEvent);
            metrics.recordSubprocess(subprocessEvent);
        });
    }

    @Test
    void recordSubprocess_withNoRecordedCalls_recordsZeroCountsWithoutThrowing() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setSubprocessExecutionId("exec-no-calls");
        event.setSubprocessElementId("Activity_agent");
        event.setStartTime(Instant.parse("2024-01-01T00:00:00Z"));
        event.setEndTime(Instant.parse("2024-01-01T00:00:01Z"));

        assertDoesNotThrow(() -> metrics.recordSubprocess(event));
    }
}
