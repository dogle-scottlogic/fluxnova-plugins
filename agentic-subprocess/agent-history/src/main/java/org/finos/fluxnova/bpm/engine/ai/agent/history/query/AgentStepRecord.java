package org.finos.fluxnova.bpm.engine.ai.agent.history.query;

import lombok.Getter;

import java.time.Instant;

/**
 * Represents a row from {@code ACT_HI_AGENT_STEP}.
 */
@Getter
public class AgentStepRecord {

    private final String id;
    private final String subprocessExecutionId;
    private final String processInstanceId;
    private final String eventType;
    private final long sequenceCounter;
    private final Instant timestamp;
    private final Integer loopIndex;
    private final String toolCallId;
    private final String toolName;
    private final String toolElementId;
    private final Long promptTokens;
    private final Long completionTokens;
    private final String responseType;
    private final Integer toolCallCount;
    private final String promptMessages;
    private final String responseContent;
    private final Long durationMs;
    private final String status;
    private final String errorMessage;
    private final String toolInput;
    private final String toolOutput;

    public AgentStepRecord(String id, String subprocessExecutionId, String processInstanceId,
            String eventType, long sequenceCounter, Instant timestamp, Integer loopIndex,
            String toolCallId, String toolName, String toolElementId, Long promptTokens,
            Long completionTokens, String responseType, Integer toolCallCount,
            String promptMessages, String responseContent,
            Long durationMs, String status, String errorMessage,
            String toolInput, String toolOutput) {
        this.id = id;
        this.subprocessExecutionId = subprocessExecutionId;
        this.processInstanceId = processInstanceId;
        this.eventType = eventType;
        this.sequenceCounter = sequenceCounter;
        this.timestamp = timestamp;
        this.loopIndex = loopIndex;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolElementId = toolElementId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.responseType = responseType;
        this.toolCallCount = toolCallCount;
        this.promptMessages = promptMessages;
        this.responseContent = responseContent;
        this.durationMs = durationMs;
        this.status = status;
        this.errorMessage = errorMessage;
        this.toolInput = toolInput;
        this.toolOutput = toolOutput;
    }
}
