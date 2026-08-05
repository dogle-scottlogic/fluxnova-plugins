package org.finos.fluxnova.bpm.engine.ai.agent.history.query;

import java.util.Date;

/**
 * Represents a row from {@code ACT_HI_AGENT_STEP}.
 */
public class AgentStepRecord {

    private final String id;
    private final String subprocessExecutionId;
    private final String processInstanceId;
    private final String eventType;
    private final long sequenceCounter;
    private final Date timestamp;
    private final Integer loopIndex;
    private final String toolCallId;
    private final String toolName;
    private final String toolElementId;
    private final Long promptTokens;
    private final Long completionTokens;
    private final String responseType;
    private final Integer toolCallCount;
    private final Long durationMs;
    private final String status;
    private final String errorMessage;

    public AgentStepRecord(String id, String subprocessExecutionId, String processInstanceId,
            String eventType, long sequenceCounter, Date timestamp, Integer loopIndex,
            String toolCallId, String toolName, String toolElementId, Long promptTokens,
            Long completionTokens, String responseType, Integer toolCallCount, Long durationMs,
            String status, String errorMessage) {
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
        this.durationMs = durationMs;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public String getId() { return id; }
    public String getSubprocessExecutionId() { return subprocessExecutionId; }
    public String getProcessInstanceId() { return processInstanceId; }
    public String getEventType() { return eventType; }
    public long getSequenceCounter() { return sequenceCounter; }
    public Date getTimestamp() { return timestamp; }
    public Integer getLoopIndex() { return loopIndex; }
    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public String getToolElementId() { return toolElementId; }
    public Long getPromptTokens() { return promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public String getResponseType() { return responseType; }
    public Integer getToolCallCount() { return toolCallCount; }
    public Long getDurationMs() { return durationMs; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
