package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job;

import org.finos.fluxnova.bpm.engine.ProcessEngineServices;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentContextSpec;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.AgentContextResolver;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelContentCaptureProperties;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelMetrics;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelTracing;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AgentTerminationHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.impl.interceptor.CommandContext;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.JobManager;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.shared.model.ToolInvocationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentOrchestrationJobHandlerTest {

    private static final String PROC_DEF_ID = "procDef:1:abc";
    private static final String PROCESS_INSTANCE_ID = "proc-inst-001";
    private static final String SCOPE_EXECUTION_ID = "scope-exec-001";
    private static final String ELEMENT_ID = "agentSubprocess";

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private AgentConfigRegistry agentConfigRegistry;
    @Mock
    private AgentToolCatalogueRegistry toolCatalogueRegistry;
    @Mock
    private AgentContextSpecRegistry contextSpecRegistry;
    @Mock
    private AgentContextResolver contextResolver;
    @Mock
    private LlmService llmService;
    @Mock
    private ToolInvocationService toolInvocationService;
    @Mock
    private AgentStateManager stateManager;
    @Mock
    private AgentTerminationHandler terminationHandler;
    @Mock
    private AgentOtelMetrics otelMetrics;
    @Mock
    private AgentOtelTracing otelTracing;
    @Mock
    private AgentOtelContentCaptureProperties contentCaptureProperties;
    @Mock
    private ExecutionEntity execution;
    @Mock
    private CommandContext commandContext;
    @Mock
    private JobManager jobManager;

    private AgentOrchestrationJobHandler handler;

    private AgentConfig agentConfig;
    private AgentToolCatalogue toolCatalogue;
    private AgentContextSpec contextSpec;

    @BeforeEach
    void setUp() {
        handler = new AgentOrchestrationJobHandler(agentConfigRegistry, toolCatalogueRegistry,
                contextSpecRegistry, contextResolver, llmService, toolInvocationService,
                stateManager, terminationHandler, otelMetrics, otelTracing,
                contentCaptureProperties);

        agentConfig = new AgentConfig(PROC_DEF_ID, ELEMENT_ID, "ollama", "llama3",
                "You are an agent.", ELEMENT_ID);
        toolCatalogue = new AgentToolCatalogue(PROC_DEF_ID, ELEMENT_ID,
                List.of(new AgentToolEntry("taskA", "Task A", "Does A", Set.of(),
                        Set.of("resultA"))));
        contextSpec = new AgentContextSpec(PROC_DEF_ID, ELEMENT_ID, List.of());
    }

    private void stubActiveServices() {
        ProcessEngineServices services = org.mockito.Mockito.mock(ProcessEngineServices.class);
        when(services.getRepositoryService()).thenReturn(repositoryService);
        when(services.getRuntimeService()).thenReturn(runtimeService);
        when(execution.getProcessEngineServices()).thenReturn(services);
    }

    private void stubActiveExecution() {
        when(execution.isActive()).thenReturn(true);
        when(execution.getId()).thenReturn(SCOPE_EXECUTION_ID);
        when(execution.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
        when(execution.getProcessDefinitionId()).thenReturn(PROC_DEF_ID);
        when(execution.getActivityId()).thenReturn(ELEMENT_ID);
    }

    private void stubRegistries() {
        when(agentConfigRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                .thenReturn(Optional.of(agentConfig));
        when(toolCatalogueRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                .thenReturn(Optional.of(toolCatalogue));
        when(contextSpecRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                .thenReturn(Optional.of(contextSpec));
    }

    private void stubEmptyState() {
        when(stateManager.loadToolResultBuffer(runtimeService, SCOPE_EXECUTION_ID))
                .thenReturn(new ArrayList<>());
        when(stateManager.loadHistory(runtimeService, SCOPE_EXECUTION_ID))
                .thenReturn(new ArrayList<>());
        when(stateManager.getLoopIndex(runtimeService, SCOPE_EXECUTION_ID)).thenReturn(0, 1);
        when(stateManager.incrementAndGetLoopIndex(runtimeService, SCOPE_EXECUTION_ID)).thenReturn(1);
    }

    @Test
    void getType_returnsCorrectType() {
        assertEquals("agent-orchestration-step", handler.getType());
    }

    @Nested
    class InactiveScope {

        @Test
        void execute_whenScopeInactive_exitsImmediately() {
            stubActiveServices();
            when(execution.isActive()).thenReturn(false);

            handler.execute(AgentOrchestrationConfig.forEntry(), execution, commandContext, null);

            verifyNoInteractions(agentConfigRegistry);
            verifyNoInteractions(llmService);
            verifyNoInteractions(otelMetrics, otelTracing);
        }
    }

    @Nested
    class EntryPath {

        @BeforeEach
        void setUpActiveExecution() {
            stubActiveExecution();
            stubActiveServices();
        }

        @Test
        void execute_callsLlmDispatchesToolsAndStartsDirectOtelCalls() {
            stubRegistries();
            stubEmptyState();
            ResolvedContext resolvedContext = new ResolvedContext(Map.of("customerId", "C123"));
            when(contextResolver.resolve(runtimeService, SCOPE_EXECUTION_ID, contextSpec))
                    .thenReturn(resolvedContext);

            List<ToolCallRequest> toolCalls = List.of(new ToolCallRequest("tc1", "taskA"));
            List<ConversationEntry> updatedHistory =
                    List.of(ConversationEntry.assistant("I'll check task A", toolCalls));
            LlmResponse response =
                    new LlmResponse("I'll check task A", toolCalls, updatedHistory, 7L, 3L);
            when(llmService.call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList()))
                    .thenReturn(response);
            when(toolInvocationService.invoke(eq(runtimeService), eq(SCOPE_EXECUTION_ID),
                    eq(toolCatalogue), any())).thenReturn(ToolInvocationResult.success("tc1"));

            handler.execute(AgentOrchestrationConfig.forEntry(), execution, commandContext, null);

            verify(llmService).call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList());
            verify(toolInvocationService).invoke(runtimeService, SCOPE_EXECUTION_ID, toolCatalogue,
                    toolCalls.get(0));
            verify(stateManager).saveHistory(runtimeService, SCOPE_EXECUTION_ID, updatedHistory);
            verify(stateManager).savePendingToolCalls(eq(runtimeService), eq(SCOPE_EXECUTION_ID),
                    eq(Set.of("tc1")));

            verify(otelTracing).startSubprocess(eq(SCOPE_EXECUTION_ID), eq(ELEMENT_ID),
                    eq(PROCESS_INSTANCE_ID), eq("ollama"), eq("llama3"),
                    eq("You are an agent."), anyString());
            verify(otelTracing).startLlmCall(eq(SCOPE_EXECUTION_ID), eq(1), eq("ollama"),
                    eq("llama3"), anyString());
            verify(otelMetrics).recordLlmCall(eq("ollama"), eq("llama3"), eq(7L), eq(3L),
                    anyLong());
            verify(otelTracing).endLlmCall(eq(SCOPE_EXECUTION_ID), eq(1), eq("ollama"),
                    eq("llama3"), eq(7L), eq(3L), eq("I'll check task A"));
            verify(otelTracing).startToolCall(SCOPE_EXECUTION_ID, ELEMENT_ID, "tc1", "taskA",
                    null, null);
        }

        @Test
        void execute_whenLlmReturnsNoToolCalls_completesScopeAndEndsSubprocessOtel() {
            stubRegistries();
            stubEmptyState();
            ResolvedContext resolvedContext = new ResolvedContext(Map.of());
            when(contextResolver.resolve(runtimeService, SCOPE_EXECUTION_ID, contextSpec))
                    .thenReturn(resolvedContext);
            when(stateManager.getStartTime(runtimeService, SCOPE_EXECUTION_ID)).thenReturn(null);

            List<ConversationEntry> updatedHistory =
                    List.of(ConversationEntry.assistant("All done!", List.of()));
            LlmResponse response = new LlmResponse("All done!", List.of(), updatedHistory, 0L, 0L);
            when(llmService.call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList()))
                    .thenReturn(response);
            when(otelMetrics.recordSubprocess(eq(SCOPE_EXECUTION_ID), eq(ELEMENT_ID),
                    eq("ollama"), eq("llama3"), eq(1), isNull(), any(), eq(0L), eq(0L)))
                            .thenReturn(0L);

            handler.execute(AgentOrchestrationConfig.forEntry(), execution, commandContext, null);

            verify(terminationHandler).complete(runtimeService, SCOPE_EXECUTION_ID);
            verifyNoInteractions(toolInvocationService);
            verify(otelMetrics).recordSubprocess(eq(SCOPE_EXECUTION_ID), eq(ELEMENT_ID),
                    eq("ollama"), eq("llama3"), eq(1), isNull(), any(), eq(0L), eq(0L));
            verify(otelTracing).endSubprocess(SCOPE_EXECUTION_ID, 0L, 0L, 1, 0L, "All done!");
        }

        @Test
        void execute_whenToolCatalogueEmpty_completesScope() {
            AgentToolCatalogue emptyCatalogue =
                    new AgentToolCatalogue(PROC_DEF_ID, ELEMENT_ID, List.of());
            when(agentConfigRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.of(agentConfig));
            when(toolCatalogueRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.of(emptyCatalogue));
            stubEmptyState();
            when(stateManager.getStartTime(runtimeService, SCOPE_EXECUTION_ID)).thenReturn(null);

            handler.execute(AgentOrchestrationConfig.forEntry(), execution, commandContext, null);

            verify(terminationHandler).complete(runtimeService, SCOPE_EXECUTION_ID);
            verifyNoInteractions(llmService);
            verify(otelMetrics).recordSubprocess(eq(SCOPE_EXECUTION_ID), eq(ELEMENT_ID),
                    eq("ollama"), eq("llama3"), eq(0), isNull(), any(), eq(0L), eq(0L));
        }

        @Test
        void execute_whenNoContextSpec_usesEmptyFallback() {
            when(agentConfigRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.of(agentConfig));
            when(toolCatalogueRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.of(toolCatalogue));
            when(contextSpecRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.empty());
            stubEmptyState();

            ResolvedContext resolvedContext = new ResolvedContext(Map.of());
            when(contextResolver.resolve(eq(runtimeService), eq(SCOPE_EXECUTION_ID),
                    any(AgentContextSpec.class))).thenReturn(resolvedContext);

            LlmResponse response = new LlmResponse("Done", List.of(),
                    List.of(ConversationEntry.assistant("Done", List.of())), 0L, 0L);
            when(llmService.call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList()))
                    .thenReturn(response);

            handler.execute(AgentOrchestrationConfig.forEntry(), execution, commandContext, null);

            verify(contextResolver).resolve(eq(runtimeService), eq(SCOPE_EXECUTION_ID),
                    any(AgentContextSpec.class));
            verify(terminationHandler).complete(runtimeService, SCOPE_EXECUTION_ID);
        }

        @Test
        void execute_whenAgentConfigMissing_throwsIllegalState() {
            when(agentConfigRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.empty());
            stubEmptyState();

            assertThrows(IllegalStateException.class,
                    () -> handler.execute(AgentOrchestrationConfig.forEntry(), execution,
                            commandContext, null));
        }

        @Test
        void execute_whenToolCatalogueMissing_throwsIllegalState() {
            when(agentConfigRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.of(agentConfig));
            when(toolCatalogueRegistry.resolve(repositoryService, PROC_DEF_ID, ELEMENT_ID))
                    .thenReturn(Optional.empty());
            stubEmptyState();

            assertThrows(IllegalStateException.class,
                    () -> handler.execute(AgentOrchestrationConfig.forEntry(), execution,
                            commandContext, null));
        }
    }

    @Nested
    class ToolCompletionPath {

        @BeforeEach
        void setUpActiveExecution() {
            when(execution.isActive()).thenReturn(true);
            when(execution.getId()).thenReturn(SCOPE_EXECUTION_ID);
            when(execution.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
            stubActiveServices();
        }

        @Test
        void execute_absorbsResultAndWaitsForMore() {
            ToolResult toolResult = new ToolResult("tc1", "taskA", null);
            when(stateManager.isPendingToolCall(runtimeService, SCOPE_EXECUTION_ID, "tc1"))
                    .thenReturn(true);
            when(stateManager.completeToolCall(runtimeService, SCOPE_EXECUTION_ID, "tc1"))
                    .thenReturn(false);

            handler.execute(AgentOrchestrationConfig.forToolCompletion(toolResult), execution,
                    commandContext, null);

            verify(stateManager).appendToResultBuffer(runtimeService, SCOPE_EXECUTION_ID, toolResult);
            verify(stateManager).completeToolCall(runtimeService, SCOPE_EXECUTION_ID, "tc1");
            verifyNoInteractions(llmService);
            verifyNoInteractions(otelMetrics, otelTracing);
        }

        @Test
        void execute_allDone_callsLlm() {
            when(execution.getProcessDefinitionId()).thenReturn(PROC_DEF_ID);
            when(execution.getActivityId()).thenReturn(ELEMENT_ID);
            stubRegistries();
            ToolResult toolResult = new ToolResult("tc1", "taskA", null);
            when(stateManager.isPendingToolCall(runtimeService, SCOPE_EXECUTION_ID, "tc1"))
                    .thenReturn(true);
            when(stateManager.completeToolCall(runtimeService, SCOPE_EXECUTION_ID, "tc1"))
                    .thenReturn(true);
            when(stateManager.getLoopIndex(runtimeService, SCOPE_EXECUTION_ID)).thenReturn(0, 1);
            when(stateManager.incrementAndGetLoopIndex(runtimeService, SCOPE_EXECUTION_ID))
                    .thenReturn(1);

            ResolvedContext resolvedContext = new ResolvedContext(Map.of("resultA", "value"));
            when(contextResolver.resolve(runtimeService, SCOPE_EXECUTION_ID, contextSpec))
                    .thenReturn(resolvedContext);

            when(stateManager.loadToolResultBuffer(runtimeService, SCOPE_EXECUTION_ID))
                    .thenReturn(new ArrayList<>(List.of(toolResult)));

            List<ConversationEntry> existingHistory = new ArrayList<>(List.of(
                    ConversationEntry.assistant("Checking",
                            List.of(new ToolCallRequest("tc1", "taskA")))));
            when(stateManager.loadHistory(runtimeService, SCOPE_EXECUTION_ID))
                    .thenReturn(existingHistory);
            when(stateManager.getStartTime(runtimeService, SCOPE_EXECUTION_ID)).thenReturn(null);

            LlmResponse response = new LlmResponse("Done!", List.of(),
                    List.of(ConversationEntry.assistant("Done!", List.of())), 0L, 0L);
            when(llmService.call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList()))
                    .thenReturn(response);

            handler.execute(AgentOrchestrationConfig.forToolCompletion(toolResult), execution,
                    commandContext, null);

            verify(stateManager).appendToResultBuffer(runtimeService, SCOPE_EXECUTION_ID, toolResult);
            verify(llmService).call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList());
            verify(terminationHandler).complete(runtimeService, SCOPE_EXECUTION_ID);
            verify(otelTracing).startLlmCall(eq(SCOPE_EXECUTION_ID), eq(1), eq("ollama"),
                    eq("llama3"), anyString());
        }

        @Test
        void execute_duplicateToolCallId_discarded() {
            ToolResult toolResult = new ToolResult("tc-unknown", "taskA", null);
            when(stateManager.isPendingToolCall(runtimeService, SCOPE_EXECUTION_ID, "tc-unknown"))
                    .thenReturn(false);

            handler.execute(AgentOrchestrationConfig.forToolCompletion(toolResult), execution,
                    commandContext, null);

            verify(stateManager, never()).appendToResultBuffer(any(), any(), any());
            verifyNoInteractions(llmService);
            verifyNoInteractions(otelMetrics, otelTracing);
        }
    }

    @Nested
    class DispatchFailures {

        @BeforeEach
        void setUpActiveExecution() {
            stubActiveExecution();
            stubActiveServices();
        }

        @Test
        void execute_allToolsFail_synthesisesCompletionJobPerFailure() {
            stubRegistries();
            stubEmptyState();
            ResolvedContext resolvedContext = new ResolvedContext(Map.of());
            when(contextResolver.resolve(runtimeService, SCOPE_EXECUTION_ID, contextSpec))
                    .thenReturn(resolvedContext);

            List<ToolCallRequest> toolCalls = List.of(new ToolCallRequest("tc1", "taskA"),
                    new ToolCallRequest("tc2", "unknownTask"));
            LlmResponse response = new LlmResponse("Checking", toolCalls,
                    List.of(ConversationEntry.assistant("Checking", toolCalls)), 0L, 0L);
            when(llmService.call(eq(agentConfig), eq(toolCatalogue), eq(resolvedContext), anyList()))
                    .thenReturn(response);
            when(toolInvocationService.invoke(eq(runtimeService), eq(SCOPE_EXECUTION_ID),
                    eq(toolCatalogue), any())).thenReturn(ToolInvocationResult.failure("tc1", "Failed"))
                            .thenReturn(ToolInvocationResult.failure("tc2", "Unknown tool"));
            when(commandContext.getJobManager()).thenReturn(jobManager);

            handler.execute(AgentOrchestrationConfig.forEntry(), execution, commandContext, null);

            verify(stateManager).savePendingToolCalls(eq(runtimeService), eq(SCOPE_EXECUTION_ID),
                    eq(Set.of("tc1", "tc2")));
            verify(jobManager, times(2)).insertAndHintJobExecutor(any(MessageEntity.class));
            verify(stateManager, never()).appendAllToResultBuffer(any(), any(), any());
        }
    }

    @Nested
    class ConfigDeserialization {

        @Test
        void newConfiguration_deserializesFromCanonicalString() {
            AgentOrchestrationConfig original = AgentOrchestrationConfig.forEntry();
            String canonical = original.toCanonicalString();

            AgentOrchestrationConfig restored = handler.newConfiguration(canonical);

            assertFalse(restored.hasToolResult());
        }
    }
}
