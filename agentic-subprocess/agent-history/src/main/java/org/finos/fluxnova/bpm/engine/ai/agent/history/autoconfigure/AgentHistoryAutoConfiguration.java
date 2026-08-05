package org.finos.fluxnova.bpm.engine.ai.agent.history.autoconfigure;

import org.finos.fluxnova.bpm.engine.ai.agent.history.handler.AgentHistoryEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.history.handler.AgentHistoryEventHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.history.query.AgentHistoryQuery;
import org.finos.fluxnova.bpm.engine.ai.agent.history.rest.AgentHistoryRestController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for the agent history module.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link AgentHistoryEventHandler} — persists history events to the DB.</li>
 *   <li>{@link AgentHistoryEnginePlugin} — registers the handler with the process engine.</li>
 *   <li>{@link AgentHistoryQuery} — JDBC-based query service.</li>
 *   <li>{@link AgentHistoryRestController} — REST endpoint.</li>
 * </ul>
 */
@AutoConfiguration
public class AgentHistoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentHistoryEventHandler agentHistoryEventHandler(DataSource dataSource) {
        return new AgentHistoryEventHandler(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentHistoryEnginePlugin agentHistoryEnginePlugin(
            AgentHistoryEventHandler agentHistoryEventHandler) {
        return new AgentHistoryEnginePlugin(agentHistoryEventHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentHistoryQuery agentHistoryQuery(DataSource dataSource) {
        return new AgentHistoryQuery(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentHistoryRestController agentHistoryRestController(AgentHistoryQuery query) {
        return new AgentHistoryRestController(query);
    }
}
