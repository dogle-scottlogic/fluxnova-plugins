package org.finos.fluxnova.bpm.engine.ai.agent.history.event;

import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent;

import java.util.Date;

/**
 * History event carrying the state of a single agent subprocess execution.
 *
 * <p>One event is fired with event type {@code agent-subprocess:start} when the subprocess is
 * entered, and another with event type {@code agent-subprocess:end} when it terminates. The end
 * event carries the accumulated token counts, iteration count, and final LLM output.
 *
 * <p>Both events are persisted to {@code ACT_HI_AGENT_SUBPROCESS}: the start event inserts the
 * row; the end event updates it in place.
 */
public class AgentSubprocessHistoryEvent extends HistoryEvent {

    private String subprocessElementId;
    private String subprocessExecutionId;
    private String provider;
    private String model;
    private String goal;
    private Date startTime;
    private Date endTime;
    private String finalOutput;
    private int iterationCount;
    private long totalPromptTokens;
    private long totalCompletionTokens;

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getFinalOutput() {
        return finalOutput;
    }

    public void setFinalOutput(String finalOutput) {
        this.finalOutput = finalOutput;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public void setIterationCount(int iterationCount) {
        this.iterationCount = iterationCount;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(long totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(long totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }
}
