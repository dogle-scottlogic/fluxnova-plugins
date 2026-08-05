package org.finos.fluxnova.bpm.engine.ai.agent.history.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

/**
 * DTO representing the full history of a single agent subprocess execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSubprocessHistoryDto {

    private String subprocessExecutionId;
    private String processInstanceId;
    private String processDefinitionKey;
    private String elementId;
    private String provider;
    private String model;
    private String goal;
    private String finalOutput;
    private Integer iterations;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long executionTime;
    private Date startTime;
    private Date endTime;
    private List<AgentToolCallDto> toolCalls;

    @JsonProperty("step-history")
    private List<AgentStepDto> stepHistory;

    public String getSubprocessExecutionId() { return subprocessExecutionId; }
    public void setSubprocessExecutionId(String subprocessExecutionId) { this.subprocessExecutionId = subprocessExecutionId; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public String getFinalOutput() { return finalOutput; }
    public void setFinalOutput(String finalOutput) { this.finalOutput = finalOutput; }

    public Integer getIterations() { return iterations; }
    public void setIterations(Integer iterations) { this.iterations = iterations; }

    public Long getTotalPromptTokens() { return totalPromptTokens; }
    public void setTotalPromptTokens(Long totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }

    public Long getTotalCompletionTokens() { return totalCompletionTokens; }
    public void setTotalCompletionTokens(Long totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }

    public Long getExecutionTime() { return executionTime; }
    public void setExecutionTime(Long executionTime) { this.executionTime = executionTime; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public List<AgentToolCallDto> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<AgentToolCallDto> toolCalls) { this.toolCalls = toolCalls; }

    public List<AgentStepDto> getStepHistory() { return stepHistory; }
    public void setStepHistory(List<AgentStepDto> stepHistory) { this.stepHistory = stepHistory; }
}
