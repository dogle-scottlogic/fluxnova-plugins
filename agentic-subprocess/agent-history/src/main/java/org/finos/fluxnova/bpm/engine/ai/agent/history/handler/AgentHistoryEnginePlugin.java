package org.finos.fluxnova.bpm.engine.ai.agent.history.handler;

import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Process engine plugin that creates the agent history DB tables on startup.
 *
 * <p>Schema creation uses Spring's {@link ResourceDatabasePopulator} with {@code IF NOT EXISTS}
 * DDL — the same pattern as the engine itself — so no external migration tool is required.
 *
 * <p>{@link AgentHistoryEventHandler} is a Spring-managed {@code HistoryEventHandler} bean and is
 * therefore automatically registered by the Fluxnova Spring Boot starter's
 * {@code DefaultHistoryConfiguration}. This plugin does not register it a second time.
 */
public class AgentHistoryEnginePlugin extends AbstractProcessEnginePlugin {

    private static final Logger LOG = LoggerFactory.getLogger(AgentHistoryEnginePlugin.class);
    private static final String SCHEMA_SCRIPT = "db/create/agent-history.sql";

    private final DataSource dataSource;

    public AgentHistoryEnginePlugin(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(SCHEMA_SCRIPT));
        populator.setSeparator(";");
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        LOG.info("Agent history schema initialised from {}", SCHEMA_SCRIPT);
    }
}
