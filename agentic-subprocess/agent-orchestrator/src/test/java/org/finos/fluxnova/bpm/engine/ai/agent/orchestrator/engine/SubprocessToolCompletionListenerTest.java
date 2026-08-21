package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.ProcessEngineServices;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelMetrics;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelTracing;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.interceptor.CommandContext;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.JobManager;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.finos.fluxnova.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubprocessToolCompletionListenerTest {

    private static final String SCOPE_EXECUTION_ID = "scope-exec-001";
    private static final String SCOPE_ACTIVITY_ID = "agentSubprocess";
    private static final String ACTIVITY_ID = "creditScoreCheck";
    private static final String TOOL_CALL_ID = "tc-001";

    @Mock
    private ExecutionEntity execution;

    @Mock
    private ExecutionEntity parentExecution;

    @Mock
    private CommandContext commandContext;

    @Mock
    private JobManager jobManager;

    @Mock
    private AgentStateManager stateManager;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ProcessEngineServices processEngineServices;

    @Mock
    private AgentOtelMetrics otelMetrics;

    @Mock
    private AgentOtelTracing otelTracing;

    private SubprocessToolCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new SubprocessToolCompletionListener(stateManager, otelMetrics, otelTracing);
    }

    private void stubToolCallExecution() {
        when(execution.getVariable("_agentToolCallId")).thenReturn(TOOL_CALL_ID);
        when(execution.getVariable("_agentToolCallError")).thenReturn(null);
        when(execution.getVariable("_agentToolOutput")).thenReturn("ok");
        when(execution.getCurrentActivityId()).thenReturn(ACTIVITY_ID);
        when(execution.getCurrentActivityName()).thenReturn(ACTIVITY_ID);
        when(execution.getParent()).thenReturn(parentExecution);
        when(parentExecution.isScope()).thenReturn(true);
        when(parentExecution.getId()).thenReturn(SCOPE_EXECUTION_ID);
        when(parentExecution.getActivityId()).thenReturn(SCOPE_ACTIVITY_ID);
        when(parentExecution.getProcessEngineServices()).thenReturn(processEngineServices);
        when(processEngineServices.getRuntimeService()).thenReturn(runtimeService);
    }

    @Nested
    class NoToolCallId {

        @Test
        void notify_whenNoToolCallId_skips() {
            when(execution.getVariable("_agentToolCallId")).thenReturn(null);

            listener.notify(execution);

            verify(execution).getVariable("_agentToolCallId");
            verify(execution).getId();
            verifyNoInteractions(otelMetrics, otelTracing);
        }
    }

    @Nested
    class ToolCompletion {

        @Test
        void notify_createsJobAndRecordsDirectOtelCalls() {
            stubToolCallExecution();
            when(commandContext.getJobManager()).thenReturn(jobManager);
            when(stateManager.getToolRequestTime(runtimeService, SCOPE_EXECUTION_ID, TOOL_CALL_ID))
                    .thenReturn(Instant.parse("2024-01-01T00:00:00Z"));

            try (MockedStatic<Context> contextMock = mockStatic(Context.class);
                    MockedStatic<ClockUtil> clockMock = mockStatic(ClockUtil.class)) {
                contextMock.when(Context::getCommandContext).thenReturn(commandContext);
                clockMock.when(ClockUtil::getCurrentTime)
                        .thenReturn(Date.from(Instant.parse("2024-01-01T00:00:00.500Z")));
                listener.notify(execution);
            }

            verify(otelMetrics).recordToolCall(SCOPE_EXECUTION_ID, SCOPE_ACTIVITY_ID, TOOL_CALL_ID,
                    ACTIVITY_ID, ACTIVITY_ID, false, 500L);
            verify(otelTracing).endToolCall(TOOL_CALL_ID, ACTIVITY_ID, ACTIVITY_ID, false, null,
                    "ok");

            ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
            verify(jobManager).insertAndHintJobExecutor(captor.capture());

            MessageEntity job = captor.getValue();
            assertEquals(AgentOrchestrationJobHandler.TYPE, job.getJobHandlerType());
            assertEquals(parentExecution, job.getExecution());

            AgentOrchestrationConfig config =
                    AgentOrchestrationConfig.fromCanonicalString(job.getJobHandlerConfigurationRaw());
            assertTrue(config.hasToolResult());
            assertEquals(TOOL_CALL_ID, config.toolResult().toolCallId());
            assertEquals(ACTIVITY_ID, config.toolResult().toolElementId());
            assertNull(config.toolResult().errorMessage());
        }
    }
}
