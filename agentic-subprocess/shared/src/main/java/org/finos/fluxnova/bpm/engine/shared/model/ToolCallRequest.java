package org.finos.fluxnova.bpm.engine.shared.model;

/**
 * Represents a single tool call requested by the LLM.
 *
 * @param toolCallId  the unique correlation id assigned by the LLM for this call
 * @param toolId      the BPMN element id of the tool activity to invoke
 * @param arguments   the JSON-encoded arguments the LLM supplied for this call, or {@code null}
 *                    if the LLM provided no arguments
 */
public record ToolCallRequest(String toolCallId, String toolId, String arguments) {

    /** Convenience constructor for cases where no arguments are needed (e.g. tests). */
    public ToolCallRequest(String toolCallId, String toolId) {
        this(toolCallId, toolId, null);
    }
}
