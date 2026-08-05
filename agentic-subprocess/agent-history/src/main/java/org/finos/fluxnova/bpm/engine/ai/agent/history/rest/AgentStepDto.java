package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO representing a single step in the agent execution timeline, to be included in the
 * {@code step-history} array of the subprocess history response.
 *
 * <p>Fields are only serialised when non-null, so the shape of each step entry varies by event
 * type.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStepDto {

    private String type;
    private Instant timestamp;
    private String elementId;
    private String provider;
    private String model;
    private Integer loopIndex;
    private Integer messageCount;
    private Long promptTokens;
    private Long completionTokens;
    private String responseType;
    private Integer toolCallCount;
    /** JSON array of conversation messages sent to the LLM; present on {@code agent-llm:request} steps. */
    @JsonRawValue
    private String promptMessages;
    /** The LLM's text response; present on {@code agent-llm:response} steps. */
    private String responseContent;
    private String toolCallId;
    private String toolName;
    private String toolElementId;
    private Long durationMs;
    private String status;
    private String errorMessage;
    private String toolInput;
    private String toolOutput;
}
