package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import org.finos.fluxnova.bpm.engine.ai.agent.history.query.AgentHistoryQuery;
import org.finos.fluxnova.bpm.engine.ai.agent.history.query.AgentStepRecord;
import org.finos.fluxnova.bpm.engine.ai.agent.history.query.AgentSubprocessRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller that exposes agent subprocess execution history.
 */
@RestController
@RequestMapping("/agent-history")
public class AgentHistoryRestController {

    private final AgentHistoryQuery query;

    public AgentHistoryRestController(AgentHistoryQuery query) {
        this.query = query;
    }

    /**
     * Returns the full history for the given subprocess execution ID.
     *
     * <p>Example: {@code GET /agent-history/subprocess/8f3a1c92-...}
     */
    @GetMapping("/subprocess/{subprocessExecutionId}")
    public ResponseEntity<AgentSubprocessHistoryDto> getBySubprocessExecutionId(
            @PathVariable("subprocessExecutionId") String subprocessExecutionId) {
        Optional<AgentSubprocessRecord> record = query.findByExecutionId(subprocessExecutionId);
        if (record.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<AgentStepRecord> steps = query.findStepsByExecutionId(subprocessExecutionId);
        return ResponseEntity.ok(toDto(record.get(), steps));
    }

    /**
     * Returns the full history for the given subprocess element within a process instance.
     *
     * <p>Example: {@code GET /agent-history/process/d4b72e11-.../subprocess/agentScope}
     */
    @GetMapping("/process/{processInstanceId}/subprocess/{subprocessElementId}")
    public ResponseEntity<AgentSubprocessHistoryDto> getByProcessInstanceAndElement(
            @PathVariable("processInstanceId") String processInstanceId,
            @PathVariable("subprocessElementId") String subprocessElementId) {
        Optional<AgentSubprocessRecord> record =
                query.findByProcessInstanceAndElement(processInstanceId, subprocessElementId);
        if (record.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<AgentStepRecord> steps =
                query.findStepsByExecutionId(record.get().getExecutionId());
        return ResponseEntity.ok(toDto(record.get(), steps));
    }

    private AgentSubprocessHistoryDto toDto(AgentSubprocessRecord record,
                                            List<AgentStepRecord> steps) {
        AgentSubprocessHistoryDto dto = new AgentSubprocessHistoryDto();
        dto.setSubprocessExecutionId(record.getExecutionId());
        dto.setProcessInstanceId(record.getProcessInstanceId());
        dto.setProcessDefinitionKey(record.getProcessDefinitionKey());
        dto.setElementId(record.getElementId());
        dto.setProvider(record.getProvider());
        dto.setModel(record.getModel());
        dto.setGoal(record.getGoal());
        dto.setInputVariables(record.getInputVariables());
        dto.setFinalOutput(record.getFinalOutput());
        dto.setIterations(record.getIterationCount());
        dto.setTotalPromptTokens(record.getTotalPromptTokens());
        dto.setTotalCompletionTokens(record.getTotalCompletionTokens());
        dto.setStartTime(record.getStartTime());
        dto.setEndTime(record.getEndTime());

        if (record.getStartTime() != null && record.getEndTime() != null) {
            dto.setExecutionTime(record.getEndTime().toEpochMilli() - record.getStartTime().toEpochMilli());
        }

        dto.setToolCalls(extractToolCalls(steps));
        dto.setStepHistory(toStepDtos(steps, record));
        return dto;
    }

    private List<AgentToolCallDto> extractToolCalls(List<AgentStepRecord> steps) {
        List<AgentToolCallDto> toolCalls = new ArrayList<>();
        for (AgentStepRecord step : steps) {
            if (!"agent-tool-call:completed".equals(step.getEventType())
                    && !"agent-tool-call:failed".equals(step.getEventType())) {
                continue;
            }
            AgentToolCallDto dto = new AgentToolCallDto();
            dto.setToolCallId(step.getToolCallId());
            dto.setToolName(step.getToolName());
            dto.setToolElementId(step.getToolElementId());
            dto.setLoopIndex(step.getLoopIndex());
            dto.setCompletedAt(step.getTimestamp());
            dto.setDurationMs(step.getDurationMs());
            dto.setStatus(step.getStatus());
            dto.setErrorMessage(step.getErrorMessage());

            // Populate requestedAt and toolInput from the matching :requested step
            steps.stream()
                    .filter(s -> "agent-tool-call:requested".equals(s.getEventType())
                            && step.getToolCallId() != null
                            && step.getToolCallId().equals(s.getToolCallId()))
                    .findFirst()
                    .ifPresent(requested -> {
                        dto.setRequestedAt(requested.getTimestamp());
                        dto.setToolInput(requested.getToolInput());
                    });

            dto.setToolOutput(step.getToolOutput());

            toolCalls.add(dto);
        }
        return toolCalls;
    }

    private List<AgentStepDto> toStepDtos(List<AgentStepRecord> steps,
                                          AgentSubprocessRecord subprocess) {
        List<AgentStepDto> dtos = new ArrayList<>();
        for (AgentStepRecord step : steps) {
            AgentStepDto dto = new AgentStepDto();
            dto.setType(step.getEventType());
            dto.setTimestamp(step.getTimestamp());
            dto.setLoopIndex(step.getLoopIndex());

            if ("agent-llm:request".equals(step.getEventType())
                    || "agent-llm:response".equals(step.getEventType())) {
                dto.setModel(subprocess.getModel());
                dto.setPromptTokens(step.getPromptTokens());
                dto.setCompletionTokens(step.getCompletionTokens());
                dto.setResponseType(step.getResponseType());
                dto.setToolCallCount(step.getToolCallCount());
                dto.setPromptMessages(step.getPromptMessages());
                dto.setResponseContent(step.getResponseContent());
            } else if (step.getEventType() != null && step.getEventType().startsWith("agent-tool-call:")) {
                dto.setToolCallId(step.getToolCallId());
                dto.setToolName(step.getToolName());
                dto.setToolElementId(step.getToolElementId());
                dto.setDurationMs(step.getDurationMs());
                dto.setStatus(step.getStatus());
                dto.setErrorMessage(step.getErrorMessage());
                dto.setToolInput(step.getToolInput());
                dto.setToolOutput(step.getToolOutput());
            }

            dtos.add(dto);
        }
        return dtos;
    }
}
