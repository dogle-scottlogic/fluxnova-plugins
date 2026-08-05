package org.finos.fluxnova.bpm.engine.ai.agent.history.handler;

import org.finos.fluxnova.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.finos.fluxnova.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Process engine plugin that registers the {@link AgentHistoryEventHandler} alongside the
 * engine's existing history handlers.
 *
 * <p>This plugin is registered as a Spring bean and picked up automatically by the Fluxnova Spring
 * Boot integration alongside the {@code AgentOrchestratorEnginePlugin}. The registration is purely
 * additive — the default {@code DbHistoryEventHandler} and any other custom handlers are
 * preserved.
 */
public class AgentHistoryEnginePlugin extends AbstractProcessEnginePlugin {

    private static final Logger LOG = LoggerFactory.getLogger(AgentHistoryEnginePlugin.class);

    private final AgentHistoryEventHandler historyEventHandler;

    public AgentHistoryEnginePlugin(AgentHistoryEventHandler historyEventHandler) {
        this.historyEventHandler = historyEventHandler;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        List<HistoryEventHandler> existing = configuration.getCustomHistoryEventHandlers();
        List<HistoryEventHandler> handlers = new ArrayList<>(existing != null ? existing : List.of());
        handlers.add(historyEventHandler);
        configuration.setCustomHistoryEventHandlers(handlers);
        LOG.info("Registered AgentHistoryEventHandler");
    }
}
