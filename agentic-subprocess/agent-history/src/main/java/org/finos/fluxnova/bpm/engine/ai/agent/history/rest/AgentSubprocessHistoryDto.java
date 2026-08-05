package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * DTO representing the full history of a single agent subprocess execution.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSubprocessHistoryDto {

    private String subprocessExecutionId;
    private String processInstanceId;
    private String processDefinitionKey;
    private String elementId;
    private String provider;
    private String model;
    private String goal;
    /** Snapshot of the resolved process variables at the time the subprocess started. */
    @JsonRawValue
    private String inputVariables;
    private String finalOutput;
    private Integer iterations;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long executionTime;
    private Instant startTime;
    private Instant endTime;
    private List<AgentToolCallDto> toolCalls;

    @JsonProperty("step-history")
    private List<AgentStepDto> stepHistory;
}
