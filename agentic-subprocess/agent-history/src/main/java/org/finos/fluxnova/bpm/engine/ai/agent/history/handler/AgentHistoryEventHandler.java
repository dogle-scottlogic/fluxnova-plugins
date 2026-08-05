package org.finos.fluxnova.bpm.engine.ai.agent.history.handler;

import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentLoopHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;
import org.finos.fluxnova.bpm.engine.impl.history.event.HistoryEvent;
import org.finos.fluxnova.bpm.engine.impl.history.handler.HistoryEventHandler;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists agent history events to two plugin-owned tables:
 * <ul>
 *   <li>{@code ACT_HI_AGENT_SUBPROCESS} — one row per subprocess execution, upserted on each
 *       {@link AgentSubprocessHistoryEvent}.</li>
 *   <li>{@code ACT_HI_AGENT_STEP} — one append-only row per step event (loop, LLM call, tool
 *       call).</li>
 * </ul>
 *
 * <p>On {@code PROCESS_INSTANCE_END} events the handler back-fills the {@code REMOVAL_TIME_}
 * column on all related agent rows to respect the engine's configured removal-time strategy.
 */
public class AgentHistoryEventHandler implements HistoryEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AgentHistoryEventHandler.class);

    private static final String SUBPROCESS_EVENT_TYPE_START = "agent-subprocess:start";
    private static final String SUBPROCESS_EVENT_TYPE_END = "agent-subprocess:end";

    private final JdbcTemplate jdbcTemplate;

    public AgentHistoryEventHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void handleEvent(HistoryEvent event) {
        if (event instanceof AgentSubprocessHistoryEvent subprocessEvent) {
            handleSubprocessEvent(subprocessEvent);
        } else if (event instanceof AgentLoopHistoryEvent loopEvent) {
            insertStep(loopEvent);
        } else if (event instanceof AgentLlmHistoryEvent llmEvent) {
            insertStep(llmEvent);
        } else if (event instanceof AgentToolCallHistoryEvent toolCallEvent) {
            insertStep(toolCallEvent);
        } else if (isProcessInstanceEnd(event)) {
            backfillRemovalTime(event);
        }
    }

    @Override
    public void handleEvents(List<HistoryEvent> historyEvents) {
        historyEvents.forEach(this::handleEvent);
    }

    // -----------------------------------------------------------------------
    // Subprocess event
    // -----------------------------------------------------------------------

    private void handleSubprocessEvent(AgentSubprocessHistoryEvent event) {
        if (SUBPROCESS_EVENT_TYPE_START.equals(event.getEventType())) {
            insertSubprocess(event);
        } else if (SUBPROCESS_EVENT_TYPE_END.equals(event.getEventType())) {
            updateSubprocess(event);
        }
    }

    private void insertSubprocess(AgentSubprocessHistoryEvent event) {
        String sql = """
                INSERT INTO ACT_HI_AGENT_SUBPROCESS (
                    ID_, PROC_INST_ID_, EXECUTION_ID_, PROC_DEF_KEY_,
                    ELEMENT_ID_, PROVIDER_, MODEL_, GOAL_,
                    INPUT_VARIABLES_,
                    START_TIME_, END_TIME_, FINAL_OUTPUT_,
                    ITERATION_COUNT_, TOTAL_PROMPT_TOKENS_, TOTAL_COMPLETION_TOKENS_,
                    REMOVAL_TIME_
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try {
            jdbcTemplate.update(sql,
                    event.getSubprocessExecutionId(),
                    event.getProcessInstanceId(),
                    event.getSubprocessExecutionId(),
                    event.getProcessDefinitionKey(),
                    event.getSubprocessElementId(),
                    event.getProvider(),
                    event.getModel(),
                    event.getGoal(),
                    event.getInputVariables(),
                    toTimestamp(event.getStartTime()),
                    null,
                    null,
                    0,
                    0L,
                    0L,
                    toTimestamp(event.getRemovalTime()));
        } catch (DuplicateKeyException e) {
            // Job retry: the row was already inserted on a previous attempt — safe to ignore.
            LOG.debug("Agent subprocess history row already exists for execution '{}', skipping insert",
                    event.getSubprocessExecutionId());
        } catch (Exception e) {
            LOG.error("Failed to insert agent subprocess history for execution '{}'",
                    event.getSubprocessExecutionId(), e);
        }
    }

    private void updateSubprocess(AgentSubprocessHistoryEvent event) {
        String sql = """
                UPDATE ACT_HI_AGENT_SUBPROCESS SET
                    END_TIME_ = ?,
                    FINAL_OUTPUT_ = ?,
                    ITERATION_COUNT_ = ?,
                    TOTAL_PROMPT_TOKENS_ = ?,
                    TOTAL_COMPLETION_TOKENS_ = ?,
                    REMOVAL_TIME_ = ?
                WHERE EXECUTION_ID_ = ?
                """;
        try {
            jdbcTemplate.update(sql,
                    toTimestamp(event.getEndTime()),
                    event.getFinalOutput(),
                    event.getIterationCount(),
                    event.getTotalPromptTokens(),
                    event.getTotalCompletionTokens(),
                    toTimestamp(event.getRemovalTime()),
                    event.getSubprocessExecutionId());
        } catch (Exception e) {
            LOG.error("Failed to update agent subprocess history for execution '{}'",
                    event.getSubprocessExecutionId(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Step events (loop, LLM, tool-call)
    // -----------------------------------------------------------------------

    private void insertStep(AgentLoopHistoryEvent event) {
        String sql = stepInsertSql();
        Instant timestamp = event.getEndTime() != null ? event.getEndTime() : event.getStartTime();
        try {
            jdbcTemplate.update(sql,
                    UUID.randomUUID().toString(),
                    event.getSubprocessExecutionId(),
                    event.getProcessInstanceId(),
                    event.getEventType(),
                    event.getSequenceCounter(),
                    toTimestamp(timestamp),
                    event.getLoopIndex(),
                    null, null, null,
                    null, null,
                    null, null, null, null,
                    null, null, null, null, null,
                    toTimestamp(event.getRemovalTime()));
        } catch (Exception e) {
            LOG.error("Failed to insert agent loop step for execution '{}'",
                    event.getSubprocessExecutionId(), e);
        }
    }

    private void insertStep(AgentLlmHistoryEvent event) {
        String sql = stepInsertSql();
        try {
            jdbcTemplate.update(sql,
                    UUID.randomUUID().toString(),
                    event.getSubprocessExecutionId(),
                    event.getProcessInstanceId(),
                    event.getEventType(),
                    event.getSequenceCounter(),
                    toTimestamp(event.getTimestamp()),
                    event.getLoopIndex(),
                    null, null, null,
                    nullIfZero(event.getPromptTokens()),
                    nullIfZero(event.getCompletionTokens()),
                    event.getResponseType(),
                    nullIfZero(event.getToolCallCount()),
                    event.getPromptMessages(),
                    event.getResponseContent(),
                    null, null, null, null, null,
                    toTimestamp(event.getRemovalTime()));
        } catch (Exception e) {
            LOG.error("Failed to insert agent LLM step for execution '{}'",
                    event.getSubprocessExecutionId(), e);
        }
    }

    private void insertStep(AgentToolCallHistoryEvent event) {
        String sql = stepInsertSql();
        Instant timestamp = event.getCompletedAt() != null ? event.getCompletedAt() : event.getRequestedAt();
        try {
            jdbcTemplate.update(sql,
                    UUID.randomUUID().toString(),
                    event.getSubprocessExecutionId(),
                    event.getProcessInstanceId(),
                    event.getEventType(),
                    event.getSequenceCounter(),
                    toTimestamp(timestamp),
                    event.getLoopIndex(),
                    event.getToolCallId(),
                    event.getToolName(),
                    event.getToolElementId(),
                    null, null, null, null, null, null,
                    nullIfZero(event.getDurationMs()),
                    event.getStatus(),
                    event.getErrorMessage(),
                    event.getToolInput(),
                    event.getToolOutput(),
                    toTimestamp(event.getRemovalTime()));
        } catch (Exception e) {
            LOG.error("Failed to insert agent tool-call step for execution '{}'",
                    event.getSubprocessExecutionId(), e);
        }
    }

    private String stepInsertSql() {
        return """
                INSERT INTO ACT_HI_AGENT_STEP (
                    ID_, SUBPROCESS_EXECUTION_ID_, PROC_INST_ID_,
                    EVENT_TYPE_, SEQUENCE_COUNTER_, TIMESTAMP_,
                    LOOP_INDEX_,
                    TOOL_CALL_ID_, TOOL_NAME_, TOOL_ELEMENT_ID_,
                    PROMPT_TOKENS_, COMPLETION_TOKENS_,
                    RESPONSE_TYPE_, TOOL_CALL_COUNT_,
                    PROMPT_MESSAGES_, RESPONSE_CONTENT_,
                    DURATION_MS_, STATUS_, ERROR_MESSAGE_,
                    TOOL_INPUT_, TOOL_OUTPUT_,
                    REMOVAL_TIME_
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }

    // -----------------------------------------------------------------------
    // Removal time back-fill
    // -----------------------------------------------------------------------

    private boolean isProcessInstanceEnd(HistoryEvent event) {
        // In the engine, when a process instance ends its executionId equals the processInstanceId.
        return "end".equals(event.getEventType())
                && event.getProcessInstanceId() != null
                && event.getProcessInstanceId().equals(event.getExecutionId());
    }

    private void backfillRemovalTime(HistoryEvent event) {
        if (event.getRemovalTime() == null) {
            return;
        }
        Timestamp removalTs = toTimestamp(event.getRemovalTime());
        try {
            jdbcTemplate.update(
                    "UPDATE ACT_HI_AGENT_SUBPROCESS SET REMOVAL_TIME_ = ? WHERE PROC_INST_ID_ = ?",
                    removalTs, event.getProcessInstanceId());
            jdbcTemplate.update(
                    "UPDATE ACT_HI_AGENT_STEP SET REMOVAL_TIME_ = ? WHERE PROC_INST_ID_ = ?",
                    removalTs, event.getProcessInstanceId());
        } catch (Exception e) {
            LOG.warn("Failed to back-fill removal time for process instance '{}'",
                    event.getProcessInstanceId(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Timestamp toTimestamp(java.util.Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Integer nullIfZero(int value) {
        return value == 0 ? null : value;
    }

    private Long nullIfZero(long value) {
        return value == 0L ? null : value;
    }
}
