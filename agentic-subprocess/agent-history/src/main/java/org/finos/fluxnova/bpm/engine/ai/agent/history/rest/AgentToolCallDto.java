package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO representing a single tool call within an agent subprocess execution.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentToolCallDto {

    private String toolCallId;
    private String toolName;
    private String toolElementId;
    private Integer loopIndex;
    private Instant requestedAt;
    private Instant completedAt;
    private Long durationMs;
    private String status;
    private String errorMessage;
    private String toolInput;
    private String toolOutput;
}
