package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.history.HistoryEventProcessor;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.finos.fluxnova.bpm.engine.impl.util.ClockUtil;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentHistoryEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class SubprocessToolCompletionListener implements ExecutionListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(SubprocessToolCompletionListener.class);

    private final AgentStateManager stateManager;

    public SubprocessToolCompletionListener(AgentStateManager stateManager) {
        this.stateManager = stateManager;
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
        ToolResult result = failed
                ? ToolResult.error(toolCallId, errorMessage)
                : new ToolResult(toolCallId, execution.getCurrentActivityId(), null);

        // Fire AGENT_TOOL_CALL_COMPLETED or AGENT_TOOL_CALL_FAILED
        if (scope != null) {
            fireToolCallCompletion(scope, toolCallId, execution.getCurrentActivityId(),
                    execution.getCurrentActivityName(), failed, errorMessage);
        }

        MessageEntity job = new MessageEntity();
        job.setExecution(scope);
        job.setJobHandlerType(AgentOrchestrationJobHandler.TYPE);
        job.setJobHandlerConfigurationRaw(
                AgentOrchestrationConfig.forToolCompletion(result).toCanonicalString());

        Context.getCommandContext().getJobManager().insertAndHintJobExecutor(job);
    }

    private void fireToolCallCompletion(ExecutionEntity scope, String toolCallId,
            String toolElementId, String toolName, boolean failed, String errorMessage) {
        AgentHistoryEventTypes eventType = failed
                ? AgentHistoryEventTypes.AGENT_TOOL_CALL_FAILED
                : AgentHistoryEventTypes.AGENT_TOOL_CALL_COMPLETED;
        String status = failed ? "FAILED" : "COMPLETED";

        Date completedAt = ClockUtil.getCurrentTime();
        Date requestedAt = stateManager.getToolRequestTime(
                scope.getProcessEngineServices().getRuntimeService(),
                scope.getId(), toolCallId);
        long durationMs = requestedAt != null ? completedAt.getTime() - requestedAt.getTime() : 0L;
        int loopIndex = stateManager.getLoopIndex(
                scope.getProcessEngineServices().getRuntimeService(), scope.getId());

        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
            event.setEventType(eventType.getEventName());
            event.setProcessInstanceId(scope.getProcessInstanceId());
            event.setExecutionId(scope.getId());
            event.setProcessDefinitionKey(scope.getProcessDefinitionId());
            event.setSubprocessElementId(scope.getActivityId());
            event.setSubprocessExecutionId(scope.getId());
            event.setLoopIndex(loopIndex);
            event.setToolCallId(toolCallId);
            event.setToolElementId(toolElementId);
            event.setToolName(toolName);
            event.setRequestedAt(requestedAt);
            event.setCompletedAt(completedAt);
            event.setDurationMs(durationMs);
            event.setStatus(status);
            event.setErrorMessage(errorMessage);
            return event;
        });
    }
}
