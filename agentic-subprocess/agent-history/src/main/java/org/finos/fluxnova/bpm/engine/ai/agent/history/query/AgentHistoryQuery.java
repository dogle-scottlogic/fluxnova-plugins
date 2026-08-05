package org.finos.fluxnova.bpm.engine.ai.agent.history.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-based query service for the agent history tables.
 */
public class AgentHistoryQuery {

    private final JdbcTemplate jdbcTemplate;

    public AgentHistoryQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the subprocess record for the given subprocess execution ID, or empty if not found.
     */
    public Optional<AgentSubprocessRecord> findByExecutionId(String subprocessExecutionId) {
        String sql = """
                SELECT ID_, PROC_INST_ID_, PROC_DEF_KEY_, ELEMENT_ID_,
                       PROVIDER_, MODEL_, GOAL_,
                       START_TIME_, END_TIME_, FINAL_OUTPUT_,
                       ITERATION_COUNT_, TOTAL_PROMPT_TOKENS_, TOTAL_COMPLETION_TOKENS_
                FROM ACT_HI_AGENT_SUBPROCESS
                WHERE EXECUTION_ID_ = ?
                """;
        List<AgentSubprocessRecord> results =
                jdbcTemplate.query(sql, SUBPROCESS_ROW_MAPPER, subprocessExecutionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns the subprocess record for the given process instance ID and BPMN element ID,
     * or empty if not found.
     */
    public Optional<AgentSubprocessRecord> findByProcessInstanceAndElement(String processInstanceId,
            String subprocessElementId) {
        String sql = """
                SELECT ID_, PROC_INST_ID_, PROC_DEF_KEY_, ELEMENT_ID_,
                       PROVIDER_, MODEL_, GOAL_,
                       START_TIME_, END_TIME_, FINAL_OUTPUT_,
                       ITERATION_COUNT_, TOTAL_PROMPT_TOKENS_, TOTAL_COMPLETION_TOKENS_
                FROM ACT_HI_AGENT_SUBPROCESS
                WHERE PROC_INST_ID_ = ? AND ELEMENT_ID_ = ?
                """;
        List<AgentSubprocessRecord> results =
                jdbcTemplate.query(sql, SUBPROCESS_ROW_MAPPER, processInstanceId,
                        subprocessElementId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns all step records for the given subprocess execution, ordered by sequence counter.
     */
    public List<AgentStepRecord> findStepsByExecutionId(String subprocessExecutionId) {
        String sql = """
                SELECT ID_, SUBPROCESS_EXECUTION_ID_, PROC_INST_ID_,
                       EVENT_TYPE_, SEQUENCE_COUNTER_, TIMESTAMP_,
                       LOOP_INDEX_, TOOL_CALL_ID_, TOOL_NAME_, TOOL_ELEMENT_ID_,
                       PROMPT_TOKENS_, COMPLETION_TOKENS_,
                       RESPONSE_TYPE_, TOOL_CALL_COUNT_,
                       DURATION_MS_, STATUS_, ERROR_MESSAGE_
                FROM ACT_HI_AGENT_STEP
                WHERE SUBPROCESS_EXECUTION_ID_ = ?
                ORDER BY SEQUENCE_COUNTER_ ASC, TIMESTAMP_ ASC
                """;
        return jdbcTemplate.query(sql, STEP_ROW_MAPPER, subprocessExecutionId);
    }

    // -----------------------------------------------------------------------
    // Row mappers
    // -----------------------------------------------------------------------

    private static final RowMapper<AgentSubprocessRecord> SUBPROCESS_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public AgentSubprocessRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new AgentSubprocessRecord(
                            rs.getString("ID_"),
                            rs.getString("PROC_INST_ID_"),
                            rs.getString("PROC_DEF_KEY_"),
                            rs.getString("ELEMENT_ID_"),
                            rs.getString("PROVIDER_"),
                            rs.getString("MODEL_"),
                            rs.getString("GOAL_"),
                            rs.getTimestamp("START_TIME_"),
                            rs.getTimestamp("END_TIME_"),
                            rs.getString("FINAL_OUTPUT_"),
                            rs.getInt("ITERATION_COUNT_"),
                            rs.getLong("TOTAL_PROMPT_TOKENS_"),
                            rs.getLong("TOTAL_COMPLETION_TOKENS_"));
                }
            };

    private static final RowMapper<AgentStepRecord> STEP_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentStepRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            long durationMs = rs.getLong("DURATION_MS_");
            Long durationMsValue = rs.wasNull() ? null : durationMs;
            long promptTokens = rs.getLong("PROMPT_TOKENS_");
            Long promptTokensValue = rs.wasNull() ? null : promptTokens;
            long completionTokens = rs.getLong("COMPLETION_TOKENS_");
            Long completionTokensValue = rs.wasNull() ? null : completionTokens;
            int toolCallCount = rs.getInt("TOOL_CALL_COUNT_");
            Integer toolCallCountValue = rs.wasNull() ? null : toolCallCount;
            int loopIndex = rs.getInt("LOOP_INDEX_");
            Integer loopIndexValue = rs.wasNull() ? null : loopIndex;

            return new AgentStepRecord(
                    rs.getString("ID_"),
                    rs.getString("SUBPROCESS_EXECUTION_ID_"),
                    rs.getString("PROC_INST_ID_"),
                    rs.getString("EVENT_TYPE_"),
                    rs.getLong("SEQUENCE_COUNTER_"),
                    rs.getTimestamp("TIMESTAMP_"),
                    loopIndexValue,
                    rs.getString("TOOL_CALL_ID_"),
                    rs.getString("TOOL_NAME_"),
                    rs.getString("TOOL_ELEMENT_ID_"),
                    promptTokensValue,
                    completionTokensValue,
                    rs.getString("RESPONSE_TYPE_"),
                    toolCallCountValue,
                    durationMsValue,
                    rs.getString("STATUS_"),
                    rs.getString("ERROR_MESSAGE_"));
        }
    };
}
