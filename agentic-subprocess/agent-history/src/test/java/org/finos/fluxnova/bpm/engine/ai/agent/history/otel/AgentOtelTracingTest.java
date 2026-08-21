package org.finos.fluxnova.bpm.engine.ai.agent.history.otel;

import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies {@link AgentOtelTracing} span start/end correlation, including the case where an
 * "end" event arrives without a matching "start" (e.g. the process restarted mid-flight) —
 * which must be silently ignored rather than throw. Runs against the default no-op {@code
 * OpenTelemetry} instance, since no {@code OpenTelemetryProcessEnginePlugin} is configured in
 * these unit tests.
 */
class AgentOtelTracingTest {

    private final AgentOtelTracing tracing = new AgentOtelTracing();

    private final AgentOtelContentCaptureProperties captureEnabled = captureEnabledProperties();

    @Test
    void subprocessSpan_startThenEnd_doesNotThrow() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setSubprocessExecutionId("exec-1");
        event.setSubprocessElementId("Activity_agent");
        event.setProvider("openai");
        event.setModel("gpt-4o");
        event.setTotalPromptTokens(10);
        event.setTotalCompletionTokens(5);

        assertDoesNotThrow(() -> {
            tracing.startSubprocess(event);
            tracing.endSubprocess(event, 2);
        });
    }

    @Test
    void subprocessSpan_endWithoutStart_isNoOp() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setSubprocessExecutionId("exec-orphan");

        assertDoesNotThrow(() -> tracing.endSubprocess(event, 0));
    }

    @Test
    void llmSpan_startThenEnd_correlatesByLoopIndex() {
        AgentLlmHistoryEvent request = new AgentLlmHistoryEvent();
        request.setSubprocessExecutionId("exec-1");
        request.setLoopIndex(1);
        request.setModel("gpt-4o");

        AgentLlmHistoryEvent response = new AgentLlmHistoryEvent();
        response.setSubprocessExecutionId("exec-1");
        response.setLoopIndex(1);
        response.setModel("gpt-4o");
        response.setProvider("openai");
        response.setPromptTokens(20);
        response.setCompletionTokens(8);

        assertDoesNotThrow(() -> {
            tracing.startLlmCall(request);
            tracing.endLlmCall(response);
        });
    }

    @Test
    void llmSpan_endWithoutStart_isNoOp() {
        AgentLlmHistoryEvent response = new AgentLlmHistoryEvent();
        response.setSubprocessExecutionId("exec-orphan");
        response.setLoopIndex(99);

        assertDoesNotThrow(() -> tracing.endLlmCall(response));
    }

    @Test
    void toolCallSpan_startThenFailedEnd_doesNotThrow() {
        AgentToolCallHistoryEvent requested = new AgentToolCallHistoryEvent();
        requested.setToolCallId("call-1");
        requested.setToolElementId("Activity_lookup");
        requested.setSubprocessElementId("Activity_agent");
        requested.setRequestedAt(Instant.now());

        AgentToolCallHistoryEvent failed = new AgentToolCallHistoryEvent();
        failed.setToolCallId("call-1");
        failed.setToolName("lookupCustomer");
        failed.setStatus("FAILED");
        failed.setErrorMessage("timeout");

        assertDoesNotThrow(() -> {
            tracing.startToolCall(requested);
            tracing.endToolCall(failed);
        });
    }

    @Test
    void toolCallSpan_endWithoutStart_isNoOp() {
        AgentToolCallHistoryEvent completed = new AgentToolCallHistoryEvent();
        completed.setToolCallId("call-orphan");
        completed.setStatus("COMPLETED");

        assertDoesNotThrow(() -> tracing.endToolCall(completed));
    }

    @Test
    void contentCapture_disabledByDefault_doesNotThrowWithContentPopulated() {
        AgentSubprocessHistoryEvent subprocessEvent = new AgentSubprocessHistoryEvent();
        subprocessEvent.setSubprocessExecutionId("exec-content-1");
        subprocessEvent.setGoal("Book a flight for the customer");

        AgentLlmHistoryEvent request = new AgentLlmHistoryEvent();
        request.setSubprocessExecutionId("exec-content-1");
        request.setLoopIndex(1);
        request.setPromptMessages("[{\"role\":\"user\",\"content\":\"hello\"}]");

        AgentLlmHistoryEvent response = new AgentLlmHistoryEvent();
        response.setSubprocessExecutionId("exec-content-1");
        response.setLoopIndex(1);
        response.setResponseContent("hi there");

        AgentToolCallHistoryEvent requested = new AgentToolCallHistoryEvent();
        requested.setToolCallId("call-content-1");
        requested.setToolInput("{\"city\":\"London\"}");

        AgentToolCallHistoryEvent completed = new AgentToolCallHistoryEvent();
        completed.setToolCallId("call-content-1");
        completed.setStatus("COMPLETED");
        completed.setToolOutput("{\"flightId\":\"BA123\"}");

        assertDoesNotThrow(() -> {
            tracing.startSubprocess(subprocessEvent);
            tracing.startLlmCall(request);
            tracing.endLlmCall(response);
            tracing.startToolCall(requested);
            tracing.endToolCall(completed);
            tracing.endSubprocess(subprocessEvent, 1);
        });
    }

    @Test
    void contentCapture_enabled_doesNotThrowWithContentPopulated() {
        AgentOtelTracing captureTracing = new AgentOtelTracing(captureEnabled);

        AgentSubprocessHistoryEvent subprocessEvent = new AgentSubprocessHistoryEvent();
        subprocessEvent.setSubprocessExecutionId("exec-content-2");
        subprocessEvent.setGoal("Book a flight for the customer");

        AgentLlmHistoryEvent request = new AgentLlmHistoryEvent();
        request.setSubprocessExecutionId("exec-content-2");
        request.setLoopIndex(1);
        request.setPromptMessages("[{\"role\":\"user\",\"content\":\"hello\"}]");

        AgentLlmHistoryEvent response = new AgentLlmHistoryEvent();
        response.setSubprocessExecutionId("exec-content-2");
        response.setLoopIndex(1);
        response.setResponseContent("hi there");

        AgentToolCallHistoryEvent requested = new AgentToolCallHistoryEvent();
        requested.setToolCallId("call-content-2");
        requested.setToolInput("{\"city\":\"London\"}");

        AgentToolCallHistoryEvent completed = new AgentToolCallHistoryEvent();
        completed.setToolCallId("call-content-2");
        completed.setStatus("COMPLETED");
        completed.setToolOutput("{\"flightId\":\"BA123\"}");

        assertDoesNotThrow(() -> {
            captureTracing.startSubprocess(subprocessEvent);
            captureTracing.startLlmCall(request);
            captureTracing.endLlmCall(response);
            captureTracing.startToolCall(requested);
            captureTracing.endToolCall(completed);
            captureTracing.endSubprocess(subprocessEvent, 1);
        });
    }

    private static AgentOtelContentCaptureProperties captureEnabledProperties() {
        AgentOtelContentCaptureProperties properties = new AgentOtelContentCaptureProperties();
        properties.setCaptureContent(true);
        return properties;
    }
}
