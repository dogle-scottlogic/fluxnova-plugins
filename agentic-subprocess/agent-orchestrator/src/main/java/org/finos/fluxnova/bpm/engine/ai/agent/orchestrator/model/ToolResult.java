package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The outcome of a single tool activity execution.
 *
 * <p>A result is either successful (non-null {@code toolElementId}, null
 * {@code errorMessage}) or an error (null {@code toolElementId}, non-null
 * {@code errorMessage}).
 *
 * @param toolCallId     the correlation id from the original tool-call request
 * @param toolElementId  the BPMN activity id that completed; {@code null} for error results
 * @param errorMessage   the error description if the tool failed to start or execute;
 *                       {@code null} for successful results
 * @param output         the tool's output, as set by the activity via the
 *                       {@code _agentToolOutput} process variable; {@code null} if none
 */
public record ToolResult(
    String toolCallId,
    String toolElementId,
    String errorMessage,
    String output
) {
    /** Convenience constructor for cases with no output (e.g. tests). */
    public ToolResult(String toolCallId, String toolElementId, String errorMessage) {
        this(toolCallId, toolElementId, errorMessage, null);
    }

    public static ToolResult error(String toolCallId, String message) {
        return new ToolResult(toolCallId, null, message, null);
    }

    @JsonIgnore
    public boolean isError() {
        return errorMessage != null;
    }
}
