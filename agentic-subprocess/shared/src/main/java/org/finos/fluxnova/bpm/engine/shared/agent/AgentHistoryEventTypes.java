package org.finos.fluxnova.bpm.engine.shared.agent;

import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEventType;

/**
 * History event types for the agentic subprocess.
 *
 * <p>Each constant represents one observable step in the agent execution lifecycle.
 * The event name returned by {@link #getEventName()} is the value stored in
 * {@code ACT_HI_AGENT_STEP.EVENT_TYPE_} and in {@link
 * org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent#getEventType()}.
 */
public enum AgentHistoryEventTypes implements HistoryEventType {

    // --- agent-subprocess entity ---

    /** Fires when the ad-hoc subprocess is entered and the first orchestration job is scheduled. */
    AGENT_SUBPROCESS_START("agent-subprocess", "start"),

    /** Fires when the subprocess terminates (LLM returned text-only or the tool catalogue was empty). */
    AGENT_SUBPROCESS_END("agent-subprocess", "end"),

    // --- agent-loop entity ---

    /** Fires at the beginning of each LLM round-trip cycle. */
    AGENT_LOOP_START("agent-loop", "start"),

    /** Fires after the LLM response has been processed and the next action determined. */
    AGENT_LOOP_END("agent-loop", "end"),

    // --- agent-llm entity ---

    /** Fires immediately before {@code LlmService.call()} — captures message count. */
    AGENT_LLM_REQUEST("agent-llm", "request"),

    /** Fires immediately after the {@code LlmResponse} is received — captures token usage. */
    AGENT_LLM_RESPONSE("agent-llm", "response"),

    // --- agent-tool-call entity ---

    /** Fires when the LLM has requested a tool call and it has been validated against the catalogue. */
    AGENT_TOOL_CALL_REQUESTED("agent-tool-call", "requested"),

    /** Fires when the tool activity ended successfully. */
    AGENT_TOOL_CALL_COMPLETED("agent-tool-call", "completed"),

    /** Fires when the tool activity ended in error. */
    AGENT_TOOL_CALL_FAILED("agent-tool-call", "failed");

    private final String entityType;
    private final String eventName;

    AgentHistoryEventTypes(String entityType, String eventName) {
        this.entityType = entityType;
        this.eventName = eventName;
    }

    @Override
    public String getEntityType() {
        return entityType;
    }

    /**
     * Returns the fully-qualified event name in {@code entity:event} form,
     * e.g. {@code "agent-llm:request"}.
     */
    @Override
    public String getEventName() {
        return entityType + ":" + eventName;
    }
}
