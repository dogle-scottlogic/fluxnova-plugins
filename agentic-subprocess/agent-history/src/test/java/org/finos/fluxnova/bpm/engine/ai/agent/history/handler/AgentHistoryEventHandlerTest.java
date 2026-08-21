package org.finos.fluxnova.bpm.engine.ai.agent.history.handler;

import org.finos.fluxnova.bpm.engine.ai.agent.history.otel.AgentOtelMetrics;
import org.finos.fluxnova.bpm.engine.ai.agent.history.otel.AgentOtelTracing;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentHistoryEventTypes;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link AgentHistoryEventHandler} drives {@link AgentOtelMetrics} and {@link
 * AgentOtelTracing} exactly on the "terminal" event of each entity — the point at which
 * duration/token data is fully populated — rather than on every event of that entity type, and
 * that span start hooks fire on the corresponding "initial" event.
 */
@ExtendWith(MockitoExtension.class)
class AgentHistoryEventHandlerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AgentOtelMetrics otelMetrics;

    @Mock
    private AgentOtelTracing otelTracing;

    private AgentHistoryEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AgentHistoryEventHandler(jdbcTemplate, otelMetrics, otelTracing);
    }

    @Test
    void handleEvent_llmResponse_recordsMetricAndEndsSpan() {
        AgentLlmHistoryEvent event = new AgentLlmHistoryEvent();
        event.setEventType(AgentHistoryEventTypes.AGENT_LLM_RESPONSE.getEventName());

        handler.handleEvent(event);

        verify(otelMetrics).recordLlmCall(event);
        verify(otelTracing).endLlmCall(event);
        verify(otelTracing, never()).startLlmCall(event);
    }

    @Test
    void handleEvent_llmRequest_startsSpanOnlyNoMetric() {
        AgentLlmHistoryEvent event = new AgentLlmHistoryEvent();
        event.setEventType(AgentHistoryEventTypes.AGENT_LLM_REQUEST.getEventName());

        handler.handleEvent(event);

        verify(otelTracing).startLlmCall(event);
        verify(otelMetrics, never()).recordLlmCall(event);
        verify(otelTracing, never()).endLlmCall(event);
    }

    @Test
    void handleEvent_toolCallCompleted_recordsMetricAndEndsSpan() {
        AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
        event.setEventType(AgentHistoryEventTypes.AGENT_TOOL_CALL_COMPLETED.getEventName());

        handler.handleEvent(event);

        verify(otelMetrics).recordToolCall(event);
        verify(otelTracing).endToolCall(event);
    }

    @Test
    void handleEvent_toolCallFailed_recordsMetricAndEndsSpan() {
        AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
        event.setEventType(AgentHistoryEventTypes.AGENT_TOOL_CALL_FAILED.getEventName());

        handler.handleEvent(event);

        verify(otelMetrics).recordToolCall(event);
        verify(otelTracing).endToolCall(event);
    }

    @Test
    void handleEvent_toolCallRequested_startsSpanOnlyNoMetric() {
        AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
        event.setEventType(AgentHistoryEventTypes.AGENT_TOOL_CALL_REQUESTED.getEventName());

        handler.handleEvent(event);

        verify(otelTracing).startToolCall(event);
        verify(otelMetrics, never()).recordToolCall(event);
        verify(otelTracing, never()).endToolCall(event);
    }

    @Test
    void handleEvent_subprocessEnd_recordsMetricAndEndsSpanWithSameToolCallCount() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setEventType("agent-subprocess:end");
        event.setSubprocessExecutionId("exec-1");
        when(otelMetrics.recordSubprocess(event)).thenReturn(3L);

        handler.handleEvent(event);

        verify(otelMetrics).recordSubprocess(event);
        // The tool-call count returned by the metric MUST be passed straight through to the span,
        // so gen_ai.invoke_agent.tool_calls stays consistent between the two signals.
        verify(otelTracing).endSubprocess(event, 3L);
    }

    @Test
    void handleEvent_subprocessStart_startsSpanOnlyNoMetric() {
        AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
        event.setEventType("agent-subprocess:start");
        event.setSubprocessExecutionId("exec-1");

        handler.handleEvent(event);

        verify(otelTracing).startSubprocess(event);
        verify(otelMetrics, never()).recordSubprocess(event);
        verify(otelTracing, never()).endSubprocess(event, 0L);
    }
}
