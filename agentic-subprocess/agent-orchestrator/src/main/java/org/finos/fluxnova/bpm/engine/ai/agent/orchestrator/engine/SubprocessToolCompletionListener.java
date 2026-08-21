package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelMetrics;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelTracing;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.finos.fluxnova.bpm.engine.impl.util.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class SubprocessToolCompletionListener implements ExecutionListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(SubprocessToolCompletionListener.class);

    private final AgentStateManager stateManager;
    private final AgentOtelMetrics otelMetrics;
    private final AgentOtelTracing otelTracing;

    public SubprocessToolCompletionListener(AgentStateManager stateManager,
            AgentOtelMetrics otelMetrics, AgentOtelTracing otelTracing) {
        this.stateManager = stateManager;
        this.otelMetrics = otelMetrics;
        this.otelTracing = otelTracing;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String toolCallId = (String) execution.getVariable("_agentToolCallId");
        if (toolCallId == null) {
            LOG.warn("Skipping tool result processing for execution '{}' - no toolCallId found",
                    execution.getId());
            return;
        }

        ExecutionEntity execEntity = (ExecutionEntity) execution;
        ExecutionEntity scope = execEntity.getParent();
        while (scope != null && !scope.isScope()) {
            scope = scope.getParent();
        }

        String errorMessage = (String) execution.getVariable("_agentToolCallError");
        boolean failed = errorMessage != null;
        String toolOutput = failed ? null : (String) execution.getVariable("_agentToolOutput");
        ToolResult result = failed
                ? ToolResult.error(toolCallId, errorMessage)
                : new ToolResult(toolCallId, execution.getCurrentActivityId(), null, toolOutput);

        if (scope != null) {
            recordToolCallCompletion(scope, toolCallId, execution.getCurrentActivityId(),
                    execution.getCurrentActivityName(), failed, errorMessage, toolOutput);
        }

        MessageEntity job = new MessageEntity();
        job.setExecution(scope);
        job.setJobHandlerType(AgentOrchestrationJobHandler.TYPE);
        job.setJobHandlerConfigurationRaw(
                AgentOrchestrationConfig.forToolCompletion(result).toCanonicalString());

        Context.getCommandContext().getJobManager().insertAndHintJobExecutor(job);
    }

    private void recordToolCallCompletion(ExecutionEntity scope, String toolCallId,
            String toolElementId, String toolName, boolean failed, String errorMessage,
            String toolOutput) {
        Instant completedAt = ClockUtil.getCurrentTime().toInstant();
        Instant requestedAt = stateManager.getToolRequestTime(
                scope.getProcessEngineServices().getRuntimeService(), scope.getId(), toolCallId);
        long durationMs =
                requestedAt != null ? completedAt.toEpochMilli() - requestedAt.toEpochMilli() : 0L;

        otelMetrics.recordToolCall(scope.getId(), scope.getActivityId(), toolCallId,
                toolElementId, toolName, failed, durationMs);
        otelTracing.endToolCall(toolCallId, toolElementId, toolName, failed, errorMessage,
                toolOutput);
    }
}
