package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.integration;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.autoconfigure.AgentConfigEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AdHocSubProcessCatalogueBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AgentContextSpecBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AgentToolCatalogueBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.BpmnExtensionContextSpecBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.AgentContextResolver;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelMetrics;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelTracing;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AdHocAgentOrchestrationParseListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentOrchestratorEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentSubprocessEntryListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.SubprocessToolCompletionListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AdHocSubprocessTerminator;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AgentTerminationHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.service.AdHocActivityToolInvocationServiceImpl;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.finos.fluxnova.bpm.engine.impl.jobexecutor.JobHandler;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires all five agentic plugins manually. The plugin auto-configurations are excluded because
 * their {@code @ConditionalOnBean} checks fail due to auto-config ordering (they evaluate before
 * FluxnovaBpmAutoConfiguration creates engine service beans).
 *
 * <p>
 * Only {@link LlmOrchestrationService} is mocked — everything else uses real implementations backed
 * by the in-memory Fluxnova engine.
 *
 * <p>
 * Beans that depend on engine services are now self-contained and can be instantiated directly
 * for these tests; the engine services are resolved at use sites inside the real implementations.
 */
@TestConfiguration
public class TestConfig {

    // -- Mock boundary --

    @Bean
    public LlmService llmOrchestrationService() {
        return Mockito.mock(LlmService.class);
    }

    // -- agent-config plugin --

    @Bean
    public AgentConfigExtractor agentConfigExtractor() {
        return new AgentConfigExtractor();
    }

    @Bean
    public AgentConfigRegistry agentConfigRegistry(AgentConfigExtractor extractor) {
        return new AgentConfigRegistry(extractor);
    }

    @Bean
    public AgentConfigEnginePlugin agentConfigEnginePlugin() {
        return new AgentConfigEnginePlugin();
    }

    // -- agent-tool-context-discovery plugin --

    @Bean
    public AgentToolCatalogueBuilder agentToolCatalogueBuilder() {
        return new AdHocSubProcessCatalogueBuilder();
    }

    @Bean
    public AgentContextSpecBuilder agentContextSpecBuilder() {
        return new BpmnExtensionContextSpecBuilder();
    }

    @Bean
    public AgentToolCatalogueRegistry agentToolCatalogueRegistry(
            AgentConfigRegistry configRegistry, AgentToolCatalogueBuilder builder) {
        return new AgentToolCatalogueRegistry(configRegistry, builder);
    }

    @Bean
    public AgentContextSpecRegistry agentContextSpecRegistry(AgentConfigRegistry configRegistry,
                                                             AgentContextSpecBuilder builder) {
        return new AgentContextSpecRegistry(configRegistry, builder);
    }

    @Bean
    public AgentContextResolver agentContextResolver() {
        return new AgentContextResolver();
    }

    // -- agent-tool-invocation plugin --
    // The real AdHocActivityToolInvocationServiceImpl is used here.
    // The test BPMN suppresses the default ad-hoc completion condition via
    // <completionCondition>${false}</completionCondition>, which prevents the subprocess from
    // terminating prematurely after the first synchronous tool activity completes.
    // To be reviewed with final ad-hoc subprocess semantics.

    @Bean
    public ToolInvocationService toolInvocationService() {
        return new AdHocActivityToolInvocationServiceImpl();
    }

    // -- agent-orchestrator plugin --

    @Bean
    public AgentStateManager agentStateManager() {
        return new AgentStateManager();
    }

    @Bean
    public AgentSubprocessEntryListener agentSubprocessEntryListener() {
        return new AgentSubprocessEntryListener();
    }

    @Bean
    public AgentOtelMetrics agentOtelMetrics() {
        return new AgentOtelMetrics();
    }

    @Bean
    public AgentOtelTracing agentOtelTracing() {
        return new AgentOtelTracing();
    }

    @Bean
    public SubprocessToolCompletionListener subprocessToolCompletionListener(
            AgentStateManager stateManager, AgentOtelMetrics agentOtelMetrics,
            AgentOtelTracing agentOtelTracing) {
        return new SubprocessToolCompletionListener(stateManager, agentOtelMetrics,
                agentOtelTracing);
    }

    @Bean
    public AgentTerminationHandler agentTerminationHandler() {
        return new AdHocSubprocessTerminator();
    }

    @Bean
    public AdHocAgentOrchestrationParseListener adHocAgentOrchestrationParseListener(
            AgentSubprocessEntryListener entryListener,
            SubprocessToolCompletionListener completionListener) {
        return new AdHocAgentOrchestrationParseListener(entryListener, completionListener);
    }

    @Bean
    public AgentOrchestratorEnginePlugin agentOrchestratorEnginePlugin(
            AdHocAgentOrchestrationParseListener parseListener) {
        return new AgentOrchestratorEnginePlugin(parseListener);
    }

    // The job handler and its registration plugin are combined into a single bean.
    // All engine-service-dependent beans use ObjectProvider internally, so there is
    // no circular dependency at construction time. Resolution happens at job execution
    // time, when the engine is fully available.
    //
    // Job handler registration is currently missing from AgentOrchestratorEnginePlugin
    // — this plugin fills that gap until the production code is updated.
    @Bean
    public AbstractProcessEnginePlugin jobHandlerRegistrationPlugin(
            AgentConfigRegistry configRegistry,
            AgentToolCatalogueRegistry toolCatalogueRegistry,
            AgentContextSpecRegistry contextSpecRegistry,
            AgentContextResolver contextResolver,
            LlmService llmOrchestrationService,
            ToolInvocationService toolInvocationService, AgentStateManager stateManager,
            AgentTerminationHandler terminationHandler, AgentOtelMetrics agentOtelMetrics,
            AgentOtelTracing agentOtelTracing) {
        AgentOrchestrationJobHandler handler = new AgentOrchestrationJobHandler(configRegistry,
                toolCatalogueRegistry, contextSpecRegistry, contextResolver,
                llmOrchestrationService, toolInvocationService, stateManager,
                terminationHandler, agentOtelMetrics, agentOtelTracing);
        return new AbstractProcessEnginePlugin() {
            @Override
            public void preInit(ProcessEngineConfigurationImpl config) {
                List<JobHandler> handlers = config.getCustomJobHandlers();
                if (handlers == null) {
                    handlers = new ArrayList<>();
                    config.setCustomJobHandlers(handlers);
                }
                handlers.add(handler);
            }
        };
    }
}
