package org.finos.fluxnova.bpm.engine.ai.agent.history.event;

import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent;

import java.util.Date;

/**
 * History event for a single orchestration loop (LLM round-trip cycle).
 *
 * <p>One event is fired with event type {@code agent-loop:start} when the orchestration job begins
 * executing, and another with event type {@code agent-loop:end} once the LLM response has been
 * processed and the next action determined. Both events are appended to {@code ACT_HI_AGENT_STEP}.
 */
public class AgentLoopHistoryEvent extends HistoryEvent {

    private String subprocessElementId;
    private String subprocessExecutionId;
    private int loopIndex;
    private Date startTime;
    private Date endTime;

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
}
