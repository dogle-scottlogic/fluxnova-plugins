package org.finos.fluxnova.bpm.engine.ai.agent.history.autoconfigure;

import org.finos.fluxnova.bpm.engine.ai.agent.history.handler.AgentHistoryEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.history.handler.AgentHistoryEventHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.history.otel.AgentOtelContentCaptureProperties;
import org.finos.fluxnova.bpm.engine.ai.agent.history.query.AgentHistoryQuery;
import org.finos.fluxnova.bpm.engine.ai.agent.history.rest.AgentHistoryRestController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for the agent history module.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link AgentHistoryEventHandler} — persists history events to the DB.</li>
 *   <li>{@link AgentHistoryEnginePlugin} — registers the handler with the process engine and
 *       creates the plugin's tables via {@code db/create/agent-history.sql} on first startup.</li>
 *   <li>{@link AgentHistoryQuery} — JDBC-based query service.</li>
 *   <li>{@link AgentHistoryRestController} — REST endpoint.</li>
 * </ul>
 *
 * <p>Schema creation uses {@code CREATE TABLE IF NOT EXISTS} DDL executed by
 * {@link org.springframework.jdbc.datasource.init.ResourceDatabasePopulator} inside
 * {@link AgentHistoryEnginePlugin#postProcessEngineBuild} — the same approach as the engine
 * itself, requiring no external migration tool.
 *
 * <p>Binds {@link AgentOtelContentCaptureProperties} ({@code fluxnova.ai.agent.observability.*})
 * and passes it through to {@link AgentHistoryEventHandler}, which controls whether the
 * potentially-sensitive {@code gen_ai.input.messages}/{@code .output.messages}/{@code
 * system_instructions}/{@code tool.call.*} span attributes are captured — opt-in, disabled by
 * default.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentOtelContentCaptureProperties.class)
public class AgentHistoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentHistoryEventHandler agentHistoryEventHandler(DataSource dataSource,
            AgentOtelContentCaptureProperties contentCaptureProperties) {
        return new AgentHistoryEventHandler(new JdbcTemplate(dataSource), contentCaptureProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentHistoryEnginePlugin agentHistoryEnginePlugin(DataSource dataSource) {
        return new AgentHistoryEnginePlugin(dataSource);
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
