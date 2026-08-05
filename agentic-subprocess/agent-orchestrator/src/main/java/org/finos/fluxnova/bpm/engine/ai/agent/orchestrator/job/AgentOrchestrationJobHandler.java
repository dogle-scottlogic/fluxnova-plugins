package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentContextSpec;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.AgentContextResolver;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentLoopHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AgentTerminationHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.engine.impl.interceptor.CommandContext;
import org.finos.fluxnova.bpm.engine.impl.jobexecutor.JobHandler;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.JobEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.finos.fluxnova.bpm.engine.impl.history.HistoryEventProcessor;
import org.finos.fluxnova.bpm.engine.impl.util.ClockUtil;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentHistoryEventTypes;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.shared.model.ToolInvocationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Job handler that drives a single step of the scope execution loop.
 *
 * <p>Each execution of this handler represents one turn: it reads the current
 * conversation history and any buffered tool results from scope-local variables,
 * resolves the agent configuration and tool catalogue, calls the LLM, and then
 * either dispatches the tool activities the LLM requested or signals completion
 * of the scope if the LLM returned no tool calls.
 *
 * <p>Two entry paths are handled by a single handler type:
 * <ul>
 *   <li><b>Entry</b> — a fresh turn triggered on scope entry
 *       ({@link AgentOrchestrationConfig#forEntry()}).</li>
 *   <li><b>Tool completion</b> — a continuation triggered when a dispatched tool
 *       activity finishes ({@link AgentOrchestrationConfig#forToolCompletion(ToolResult)}).
 *       The handler accumulates results until all pending tools are complete, then
 *       proceeds to the next LLM call.</li>
 * </ul>
 */
public class AgentOrchestrationJobHandler implements JobHandler<AgentOrchestrationConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentOrchestrationJobHandler.class);

    public static final String TYPE = "agent-orchestration-step";

    /**
     * Seed user turn for the first LLM call. Without it, the entry-turn conversation is
     * system-only (system prompt + context), which many models — local ones especially —
     * won't act on: they narrate instead of emitting tool calls. A user turn kicks off the loop.
     * TODO: make this configurable via an agent:config attribute (e.g. task/userPrompt).
     */
    private static final String INITIAL_USER_PROMPT =
            "Begin. Use the available tools to complete the task, then respond and stop.";

    private final AgentConfigRegistry agentConfigRegistry;
    private final AgentToolCatalogueRegistry toolCatalogueRegistry;
    private final AgentContextSpecRegistry contextSpecRegistry;
    private final AgentContextResolver contextResolver;
    private final LlmService llmService;
    private final ToolInvocationService toolInvocationService;
    private final AgentStateManager stateManager;
    private final AgentTerminationHandler AgentTerminationHandler;

    public AgentOrchestrationJobHandler(AgentConfigRegistry agentConfigRegistry,
            AgentToolCatalogueRegistry toolCatalogueRegistry,
            AgentContextSpecRegistry contextSpecRegistry, AgentContextResolver contextResolver,
            LlmService llmService,
            ToolInvocationService toolInvocationService, AgentStateManager stateManager,
            AgentTerminationHandler AgentTerminationHandler) {
        this.agentConfigRegistry = agentConfigRegistry;
        this.toolCatalogueRegistry = toolCatalogueRegistry;
        this.contextSpecRegistry = contextSpecRegistry;
        this.contextResolver = contextResolver;
        this.llmService = llmService;
        this.toolInvocationService = toolInvocationService;
        this.stateManager = stateManager;
        this.AgentTerminationHandler = AgentTerminationHandler;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void execute(AgentOrchestrationConfig orchestratorConfig, ExecutionEntity execution,
            CommandContext commandContext, String tenantId) {
        String scopeExecutionId = execution.getId();
        LOG.debug("execute() called for scope '{}', hasToolResult={}, isActive={}, " +
                        "revision='{}', thread={}",
                scopeExecutionId,
                orchestratorConfig.hasToolResult(),
                execution.isActive(),
                execution.getRevision(),
                Thread.currentThread().getName());

        LOG.debug("execution hierarchy: id='{}', parentId='{}', superExecutionId='{}', " +
                        "isScope={}, isActive={}, activityId='{}'",
                execution.getId(),
                execution.getParentId(),
                execution.getSuperExecutionId(),
                execution.isScope(),
                execution.isActive(),
                execution.getActivityId());

        RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
        RepositoryService repositoryService = execution.getProcessEngineServices().getRepositoryService();

        if (!execution.isActive()) {
            LOG.debug("Scope execution '{}' is no longer active, skipping orchestration step",
                    scopeExecutionId);
            return;
        }

        if (orchestratorConfig.hasToolResult()) {
            ToolResult result = orchestratorConfig.toolResult();

            if (!stateManager.isPendingToolCall(runtimeService, scopeExecutionId, result.toolCallId())) {
                LOG.debug(
                        "ToolResult '{}' not in pending set, discarding (duplicate or late arrival)",
                        result.toolCallId());
                return;
            }

            boolean allCompleted =
                    stateManager.completeToolCall(runtimeService, scopeExecutionId, result.toolCallId());
            LOG.debug("completeToolCall() for '{}' returned allCompleted={}", result.toolCallId(), allCompleted);

            stateManager.appendToResultBuffer(runtimeService, scopeExecutionId, result);

            if (!allCompleted) {
                LOG.debug("Not all tools completed for scope '{}', parking", scopeExecutionId);
                return;
            }
            LOG.debug("All tools completed for scope '{}', proceeding to LLM", scopeExecutionId);
            // All pending tools done — fall through to next LLM call
        }

        List<ToolResult> buffer = stateManager.loadToolResultBuffer(runtimeService, scopeExecutionId);
        List<ConversationEntry> history = stateManager.loadHistory(runtimeService, scopeExecutionId);
        history = appendToolResults(history, buffer);
        stateManager.clearToolResultBuffer(runtimeService, scopeExecutionId);

        // First turn: history is empty and the mapper would send only system messages.
        // Seed a user turn so the model actually engages the tools.
        if (history.isEmpty()) {
            history.add(ConversationEntry.user(INITIAL_USER_PROMPT));
        }

        AgentConfig agentConfig = agentConfigRegistry
                .resolve(repositoryService, execution.getProcessDefinitionId(), execution.getActivityId())
                .orElseThrow(() -> new IllegalStateException("No AgentConfig found for "
                        + execution.getProcessDefinitionId() + "/" + execution.getActivityId()));
        AgentToolCatalogue catalogue = toolCatalogueRegistry
                .resolve(repositoryService, execution.getProcessDefinitionId(), execution.getActivityId())
                .orElseThrow(() -> new IllegalStateException("No AgentToolCatalogue found for "
                        + execution.getProcessDefinitionId() + "/" + execution.getActivityId()));

        if (catalogue.tools().isEmpty()) {
            LOG.warn(
                    "Tool catalogue is empty for activity '{}' in process '{}', terminating execution '{}'",
                    execution.getActivityId(), execution.getProcessDefinitionId(),
                    scopeExecutionId);
            fireSubprocessEnd(execution, agentConfig, null);
            AgentTerminationHandler.complete(runtimeService, scopeExecutionId);
            return;
        }

        // Fire AGENT_SUBPROCESS_START on the very first turn (loop index not yet set)
        boolean isFirstTurn = stateManager.getLoopIndex(runtimeService, scopeExecutionId) == 0;
        if (isFirstTurn) {
            Date startTime = ClockUtil.getCurrentTime();
            stateManager.recordStartTime(runtimeService, scopeExecutionId, startTime);
            fireSubprocessStart(execution, agentConfig, startTime);
        }

        // Increment loop index and fire AGENT_LOOP_START
        int loopIndex = stateManager.incrementAndGetLoopIndex(runtimeService, scopeExecutionId);
        Date loopStartTime = ClockUtil.getCurrentTime();
        fireLoopStart(execution, loopIndex, loopStartTime);

        // Fallback to empty spec if no context is declared — resolver will include all process
        // variables
        AgentContextSpec contextSpec = contextSpecRegistry
                .resolve(repositoryService, execution.getProcessDefinitionId(), execution.getActivityId())
                .orElse(new AgentContextSpec(execution.getProcessDefinitionId(),
                        execution.getActivityId(), List.of()));
        ResolvedContext context = contextResolver.resolve(runtimeService, scopeExecutionId, contextSpec);

        // Fire AGENT_LLM_REQUEST
        fireLlmRequest(execution, loopIndex, agentConfig.model(), history.size());

        LlmResponse response =
                llmService.call(agentConfig, catalogue, context, history);
        LOG.debug("LLM response for scope '{}': toolCalls={}", scopeExecutionId, response.toolCalls());
        stateManager.saveHistory(runtimeService, scopeExecutionId, response.updatedHistory());
        stateManager.accumulateTokens(runtimeService, scopeExecutionId,
                response.promptTokens(), response.completionTokens());

        // Fire AGENT_LLM_RESPONSE
        String responseType = response.toolCalls().isEmpty() ? "TEXT" : "TOOL_CALLS";
        fireLlmResponse(execution, loopIndex, agentConfig.model(),
                response.promptTokens(), response.completionTokens(),
                responseType, response.toolCalls().size());

        if (response.toolCalls().isEmpty()) {
            LOG.debug("No tool calls returned, triggering termination for scope '{}'", scopeExecutionId);
            fireLoopEnd(execution, loopIndex, ClockUtil.getCurrentTime());
            fireSubprocessEnd(execution, agentConfig, response.assistantText());
            // Complete the process if tool call is empty
            AgentTerminationHandler.complete(runtimeService, scopeExecutionId);
            return;
        }

        // Fire AGENT_TOOL_CALL_REQUESTED for each tool call
        Date toolRequestTime = ClockUtil.getCurrentTime();
        for (ToolCallRequest tc : response.toolCalls()) {
            stateManager.recordToolRequestTime(runtimeService, scopeExecutionId, tc.toolCallId(),
                    toolRequestTime);
            fireToolCallRequested(execution, loopIndex, tc, toolRequestTime);
        }

        LOG.debug("Dispatching scope '{}': toolCalls='{}'", scopeExecutionId, response.toolCalls());
        dispatch(runtimeService, scopeExecutionId, catalogue, response.toolCalls(), execution, commandContext);

        fireLoopEnd(execution, loopIndex, ClockUtil.getCurrentTime());
    }

    @Override
    public AgentOrchestrationConfig newConfiguration(String canonicalString) {
        return AgentOrchestrationConfig.fromCanonicalString(canonicalString);
    }

    @Override
    public void onDelete(AgentOrchestrationConfig configuration, JobEntity jobEntity) {
        // No cleanup needed
    }

    private void dispatch(RuntimeService runtimeService, String scopeExecutionId, AgentToolCatalogue catalogue,
            List<ToolCallRequest> toolCalls, ExecutionEntity execution,
            CommandContext commandContext) {
        Set<String> pending = new HashSet<>();

        for (ToolCallRequest tc : toolCalls) {
            pending.add(tc.toolCallId());
            LOG.debug("dispatch() scope='{}' registering toolCallId='{}'", scopeExecutionId, tc.toolCallId());
            ToolInvocationResult result =
                    toolInvocationService.invoke(runtimeService, scopeExecutionId, catalogue, tc);
            if (!result.success()) {
                // Synchronous failure — no BPMN activity will complete, so no listener will fire.
                // Instantiate an equivalent completion job so the failure travels through the same
                // tool-completion path as listener-driven results, keeping the pending set
                // consistent.
                ToolResult failure = ToolResult.error(tc.toolCallId(), result.errorMessage());
                MessageEntity job = new MessageEntity();
                job.setExecution(execution);
                job.setJobHandlerType(TYPE);
                job.setJobHandlerConfigurationRaw(
                        AgentOrchestrationConfig.forToolCompletion(failure).toCanonicalString());
                commandContext.getJobManager().insertAndHintJobExecutor(job);
            }
        }
        LOG.debug("dispatch() scope='{}' saving pending set={}", scopeExecutionId, pending);
        stateManager.savePendingToolCalls(runtimeService, scopeExecutionId, pending);
    }


    private List<ConversationEntry> appendToolResults(List<ConversationEntry> history,
            List<ToolResult> results) {
        List<ConversationEntry> updated = new ArrayList<>(history);
        for (ToolResult result : results) {
            Map<String, Object> resultContent =
                    result.isError() ? Map.of("error", result.errorMessage())
                            : Map.of("status", "ok");
            updated.add(ConversationEntry.tool(result.toolCallId(), resultContent));
        }
        return updated;
    }

    // -----------------------------------------------------------------------
    // History event helpers
    // -----------------------------------------------------------------------

    private void fireSubprocessStart(ExecutionEntity execution, AgentConfig agentConfig,
            Date startTime) {
        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_SUBPROCESS_START.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setProvider(agentConfig.provider());
            event.setModel(agentConfig.model());
            event.setGoal(agentConfig.systemPrompt());
            event.setStartTime(startTime);
            return event;
        });
    }

    private void fireSubprocessEnd(ExecutionEntity execution, AgentConfig agentConfig,
            String finalOutput) {
        String scopeExecutionId = execution.getId();
        RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
        Date startTime = stateManager.getStartTime(runtimeService, scopeExecutionId);
        int iterationCount = stateManager.getLoopIndex(runtimeService, scopeExecutionId);
        long totalPromptTokens = stateManager.getTotalPromptTokens(runtimeService, scopeExecutionId);
        long totalCompletionTokens =
                stateManager.getTotalCompletionTokens(runtimeService, scopeExecutionId);
        Date endTime = ClockUtil.getCurrentTime();

        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentSubprocessHistoryEvent event = new AgentSubprocessHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_SUBPROCESS_END.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setProvider(agentConfig.provider());
            event.setModel(agentConfig.model());
            event.setGoal(agentConfig.systemPrompt());
            event.setStartTime(startTime);
            event.setEndTime(endTime);
            event.setFinalOutput(finalOutput);
            event.setIterationCount(iterationCount);
            event.setTotalPromptTokens(totalPromptTokens);
            event.setTotalCompletionTokens(totalCompletionTokens);
            return event;
        });
    }

    private void fireLoopStart(ExecutionEntity execution, int loopIndex, Date startTime) {
        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentLoopHistoryEvent event = new AgentLoopHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_LOOP_START.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setLoopIndex(loopIndex);
            event.setStartTime(startTime);
            return event;
        });
    }

    private void fireLoopEnd(ExecutionEntity execution, int loopIndex, Date endTime) {
        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentLoopHistoryEvent event = new AgentLoopHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_LOOP_END.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setLoopIndex(loopIndex);
            event.setEndTime(endTime);
            return event;
        });
    }

    private void fireLlmRequest(ExecutionEntity execution, int loopIndex, String model,
            int messageCount) {
        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentLlmHistoryEvent event = new AgentLlmHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_LLM_REQUEST.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setLoopIndex(loopIndex);
            event.setModel(model);
            event.setMessageCount(messageCount);
            event.setTimestamp(ClockUtil.getCurrentTime());
            return event;
        });
    }

    private void fireLlmResponse(ExecutionEntity execution, int loopIndex, String model,
            long promptTokens, long completionTokens, String responseType, int toolCallCount) {
        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentLlmHistoryEvent event = new AgentLlmHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_LLM_RESPONSE.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setLoopIndex(loopIndex);
            event.setModel(model);
            event.setPromptTokens(promptTokens);
            event.setCompletionTokens(completionTokens);
            event.setResponseType(responseType);
            event.setToolCallCount(toolCallCount);
            event.setTimestamp(ClockUtil.getCurrentTime());
            return event;
        });
    }

    private void fireToolCallRequested(ExecutionEntity execution, int loopIndex,
            ToolCallRequest tc, Date requestedAt) {
        HistoryEventProcessor.processHistoryEvent(producer -> {
            AgentToolCallHistoryEvent event = new AgentToolCallHistoryEvent();
            event.setEventType(AgentHistoryEventTypes.AGENT_TOOL_CALL_REQUESTED.getEventName());
            event.setProcessInstanceId(execution.getProcessInstanceId());
            event.setExecutionId(execution.getId());
            event.setProcessDefinitionKey(execution.getProcessDefinitionId());
            event.setSubprocessElementId(execution.getActivityId());
            event.setSubprocessExecutionId(execution.getId());
            event.setLoopIndex(loopIndex);
            event.setToolCallId(tc.toolCallId());
            event.setToolElementId(tc.toolId());
            event.setRequestedAt(requestedAt);
            event.setStatus("PENDING");
            return event;
        });
    }
}
