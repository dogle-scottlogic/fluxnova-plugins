package org.finos.fluxnova.bpm.engine.shared.agent;

import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent;

import java.util.Date;

/**
 * History event for a single tool call within an orchestration loop.
 *
 * <p>Three event types use this class:
 * <ul>
 *   <li>{@code agent-tool-call:requested} — fired when the LLM requests a tool call and it has
 *       been validated against the catalogue, before the BPMN ad-hoc activity is triggered.</li>
 *   <li>{@code agent-tool-call:completed} — fired when the tool activity ends successfully.</li>
 *   <li>{@code agent-tool-call:failed} — fired when the tool activity ends in error.</li>
 * </ul>
 * All three event types are appended to {@code ACT_HI_AGENT_STEP}.
 */
public class AgentToolCallHistoryEvent extends HistoryEvent {

    private String subprocessElementId;
    private String subprocessExecutionId;
    private int loopIndex;
    private String toolCallId;
    private String toolName;
    private String toolElementId;
    private Date requestedAt;
    private Date completedAt;
    private long durationMs;
    /** {@code PENDING}, {@code COMPLETED}, or {@code FAILED}. */
    private String status;
    private String errorMessage;

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

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolElementId() {
        return toolElementId;
    }

    public void setToolElementId(String toolElementId) {
        this.toolElementId = toolElementId;
    }

    public Date getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Date requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Date getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt) {
        this.completedAt = completedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
