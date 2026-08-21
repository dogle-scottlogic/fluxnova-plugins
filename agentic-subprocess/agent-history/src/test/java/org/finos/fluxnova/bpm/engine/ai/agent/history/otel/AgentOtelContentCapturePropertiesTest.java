package org.finos.fluxnova.bpm.engine.ai.agent.history.otel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the default-off behaviour of {@link AgentOtelContentCaptureProperties}, and that the
 * Spring Boot property correctly toggles {@link AgentOtelContentCaptureProperties#isCaptureContent()}.
 *
 * <p>The {@code OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT} environment-variable fallback
 * is not exercised here since JVM environment variables cannot be set from within a test without
 * a reflection-based workaround; it is a thin {@link System#getenv(String)} read so is considered
 * low risk.
 */
class AgentOtelContentCapturePropertiesTest {

    @Test
    void isCaptureContent_defaultsToFalse() {
        AgentOtelContentCaptureProperties properties = new AgentOtelContentCaptureProperties();

        assertFalse(properties.isCaptureContent());
    }

    @Test
    void isCaptureContent_reflectsExplicitlySetValue() {
        AgentOtelContentCaptureProperties properties = new AgentOtelContentCaptureProperties();

        properties.setCaptureContent(true);

        assertTrue(properties.isCaptureContent());
    }
}
