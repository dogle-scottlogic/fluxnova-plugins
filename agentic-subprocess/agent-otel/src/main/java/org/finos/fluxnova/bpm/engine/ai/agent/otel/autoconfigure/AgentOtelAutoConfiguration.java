package org.finos.fluxnova.bpm.engine.ai.agent.otel.autoconfigure;

import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelContentCaptureProperties;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelMetrics;
import org.finos.fluxnova.bpm.engine.ai.agent.otel.AgentOtelTracing;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the agent-otel observability module.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link AgentOtelMetrics} — emits {@code gen_ai.*} metrics.</li>
 *   <li>{@link AgentOtelTracing} — emits {@code gen_ai.*} spans.</li>
 * </ul>
 *
 * <p>Binds {@link AgentOtelContentCaptureProperties} ({@code fluxnova.ai.agent.observability.*})
 * and passes it through to {@link AgentOtelTracing}, which controls whether the
 * potentially-sensitive {@code gen_ai.input.messages}/{@code .output.messages}/{@code
 * system_instructions}/{@code tool.call.*} span attributes are captured — opt-in, disabled by
 * default.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentOtelContentCaptureProperties.class)
public class AgentOtelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentOtelMetrics agentOtelMetrics() {
        return new AgentOtelMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentOtelTracing agentOtelTracing(
            AgentOtelContentCaptureProperties contentCaptureProperties) {
        return new AgentOtelTracing(contentCaptureProperties);
    }
}
