package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Map;

/**
 * DTO representing a single step in the agent execution timeline, to be included in the
 * {@code step-history} array of the subprocess history response.
 *
 * <p>Fields are only serialised when non-null, so the shape of each step entry varies by event
 * type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStepDto {

    private String type;
    private Date timestamp;
    private String elementId;
    private String provider;
    private String model;
    private Integer loopIndex;
    private Integer messageCount;
    private Long promptTokens;
    private Long completionTokens;
    private String responseType;
    private Integer toolCallCount;
    private String toolCallId;
    private String toolName;
    private String toolElementId;
    private Long durationMs;
    private String status;
    private String errorMessage;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getLoopIndex() { return loopIndex; }
    public void setLoopIndex(Integer loopIndex) { this.loopIndex = loopIndex; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }

    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }

    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolElementId() { return toolElementId; }
    public void setToolElementId(String toolElementId) { this.toolElementId = toolElementId; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
