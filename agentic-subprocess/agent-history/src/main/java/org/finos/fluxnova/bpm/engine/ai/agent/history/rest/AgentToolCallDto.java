package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * DTO representing a single tool call within an agent subprocess execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentToolCallDto {

    private String toolCallId;
    private String toolName;
    private String toolElementId;
    private Integer loopIndex;
    private Date requestedAt;
    private Date completedAt;
    private Long durationMs;
    private String status;
    private String errorMessage;

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolElementId() { return toolElementId; }
    public void setToolElementId(String toolElementId) { this.toolElementId = toolElementId; }

    public Integer getLoopIndex() { return loopIndex; }
    public void setLoopIndex(Integer loopIndex) { this.loopIndex = loopIndex; }

    public Date getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Date requestedAt) { this.requestedAt = requestedAt; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
