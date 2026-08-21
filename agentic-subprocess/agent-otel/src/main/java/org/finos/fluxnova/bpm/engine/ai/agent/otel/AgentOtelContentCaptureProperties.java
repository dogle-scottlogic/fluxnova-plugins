package org.finos.fluxnova.bpm.engine.ai.agent.otel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opt-in switch for capturing potentially-sensitive GenAI content
 * (<a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/">prompt/response
 * message bodies, system instructions, and tool call arguments/results</a>) on the spans emitted
 * by {@link AgentOtelTracing}.
 *
 * <p>Defaults to {@code false} (disabled) per the GenAI semantic conventions' guidance that
 * content capture is opt-in, since agent prompts/responses and tool inputs/outputs may contain
 * sensitive business or personal data that operators may not want flowing into a tracing
 * backend.
 *
 * <p>Bound from the {@code fluxnova.ai.agent.observability} Spring Boot configuration prefix,
 * e.g.:
 * <pre>
 * fluxnova:
 *   ai:
 *     agent:
 *       observability:
 *         capture-content: true
 * </pre>
 *
 * <p>For parity with upstream OpenTelemetry GenAI instrumentations (which gate the same content
 * behind {@code OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT}), that environment variable
 * is also honoured as a fallback when the Spring Boot property is left at its default — see
 * {@link #isCaptureContent()}.
 */
@ConfigurationProperties(prefix = "fluxnova.ai.agent.observability")
public class AgentOtelContentCaptureProperties {

    private static final String CAPTURE_CONTENT_ENV_VAR = "OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT";

    private boolean captureContent = false;

    /**
     * Whether to have {@code AgentOtelTracing} set gen_ai.input.messages, gen_ai.output.messages,
     * gen_ai.system_instructions, and gen_ai.tool.call.arguments/result on spans.
     *
     * <p>Falls back to the standard {@code OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT}
     * environment variable when the Spring Boot property has not been explicitly set to
     * {@code true}, so deployments already using the upstream OTEL convention do not need a
     * Fluxnova-specific override too.
     */
    public boolean isCaptureContent() {
        return captureContent || Boolean.parseBoolean(System.getenv(CAPTURE_CONTENT_ENV_VAR));
    }

    public void setCaptureContent(boolean captureContent) {
        this.captureContent = captureContent;
    }
}
