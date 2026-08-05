package org.finos.fluxnova.bpm.engine.ai.agent.history.query;

import java.util.Date;

/**
 * Represents a row from {@code ACT_HI_AGENT_SUBPROCESS}.
 */
public class AgentSubprocessRecord {

    private final String executionId;
    private final String processInstanceId;
    private final String processDefinitionKey;
    private final String elementId;
    private final String provider;
    private final String model;
    private final String goal;
    private final Date startTime;
    private final Date endTime;
    private final String finalOutput;
    private final int iterationCount;
    private final long totalPromptTokens;
    private final long totalCompletionTokens;

    public AgentSubprocessRecord(String executionId, String processInstanceId,
            String processDefinitionKey, String elementId, String provider, String model,
            String goal, Date startTime, Date endTime, String finalOutput, int iterationCount,
            long totalPromptTokens, long totalCompletionTokens) {
        this.executionId = executionId;
        this.processInstanceId = processInstanceId;
        this.processDefinitionKey = processDefinitionKey;
        this.elementId = elementId;
        this.provider = provider;
        this.model = model;
        this.goal = goal;
        this.startTime = startTime;
        this.endTime = endTime;
        this.finalOutput = finalOutput;
        this.iterationCount = iterationCount;
        this.totalPromptTokens = totalPromptTokens;
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public String getExecutionId() { return executionId; }
    public String getProcessInstanceId() { return processInstanceId; }
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public String getElementId() { return elementId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getGoal() { return goal; }
    public Date getStartTime() { return startTime; }
    public Date getEndTime() { return endTime; }
    public String getFinalOutput() { return finalOutput; }
    public int getIterationCount() { return iterationCount; }
    public long getTotalPromptTokens() { return totalPromptTokens; }
    public long getTotalCompletionTokens() { return totalCompletionTokens; }
}
