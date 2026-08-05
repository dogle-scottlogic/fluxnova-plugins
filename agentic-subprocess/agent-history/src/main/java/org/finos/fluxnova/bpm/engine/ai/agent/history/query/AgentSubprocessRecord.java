package org.finos.fluxnova.bpm.engine.ai.agent.history.query;

import lombok.Getter;

import java.time.Instant;

/**
 * Represents a row from {@code ACT_HI_AGENT_SUBPROCESS}.
 */
@Getter
public class AgentSubprocessRecord {

    private final String executionId;
    private final String processInstanceId;
    private final String processDefinitionKey;
    private final String elementId;
    private final String provider;
    private final String model;
    private final String goal;
    private final String inputVariables;
    private final Instant startTime;
    private final Instant endTime;
    private final String finalOutput;
    private final int iterationCount;
    private final long totalPromptTokens;
    private final long totalCompletionTokens;

    public AgentSubprocessRecord(String executionId, String processInstanceId,
            String processDefinitionKey, String elementId, String provider, String model,
            String goal, String inputVariables, Instant startTime, Instant endTime,
            String finalOutput, int iterationCount, long totalPromptTokens,
            long totalCompletionTokens) {
        this.executionId = executionId;
        this.processInstanceId = processInstanceId;
        this.processDefinitionKey = processDefinitionKey;
        this.elementId = elementId;
        this.provider = provider;
        this.model = model;
        this.goal = goal;
        this.inputVariables = inputVariables;
        this.startTime = startTime;
        this.endTime = endTime;
        this.finalOutput = finalOutput;
        this.iterationCount = iterationCount;
        this.totalPromptTokens = totalPromptTokens;
        this.totalCompletionTokens = totalCompletionTokens;
    }
}
