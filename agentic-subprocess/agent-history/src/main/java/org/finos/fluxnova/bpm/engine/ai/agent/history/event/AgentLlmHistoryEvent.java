package org.finos.fluxnova.bpm.engine.ai.agent.history.event;

import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent;

import java.util.Date;

/**
 * History event for a single LLM call within an orchestration loop.
 *
 * <p>An {@code agent-llm:request} event is fired immediately before {@code LlmService.call()} is
 * invoked; an {@code agent-llm:response} event is fired once the response is received. Both events
 * are appended to {@code ACT_HI_AGENT_STEP}.
 */
public class AgentLlmHistoryEvent extends HistoryEvent {

    private String subprocessElementId;
    private String subprocessExecutionId;
    private int loopIndex;
    private String model;
    private int messageCount;
    /** Zero on {@code request} events; populated from the LLM response metadata. */
    private long promptTokens;
    /** Zero on {@code request} events; populated from the LLM response metadata. */
    private long completionTokens;
    /** {@code null} on {@code request} events; {@code "TEXT"} or {@code "TOOL_CALLS"} on response. */
    private String responseType;
    private int toolCallCount;
    private Date timestamp;

    public String getSubprocessElementId() {
        return subprocessElementId;
    }

    public void setSubprocessElementId(String subprocessElementId) {
        this.subprocessElementId = subprocessElementId;
    }

    public String getSubprocessExecutionId() {
        return subprocessExecutionId;
    }

    public void setSubprocessExecutionId(String subprocessExecutionId) {
        this.subprocessExecutionId = subprocessExecutionId;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
