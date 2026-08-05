package org.finos.fluxnova.bpm.engine.shared.agent;

import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent;

import java.time.Instant;

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
    private Instant startTime;
    private Instant endTime;

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

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}
