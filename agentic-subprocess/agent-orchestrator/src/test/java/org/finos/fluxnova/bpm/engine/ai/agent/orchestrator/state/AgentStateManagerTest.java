package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.Role;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentStateManagerTest {

        private static final String EXECUTION_ID = "exec-123";

        @Mock
        private RuntimeService runtimeService;

        private AgentStateManager stateManager;

        @BeforeEach
        void setUp() {
                stateManager = new AgentStateManager();
        }

        @Nested
        class ConversationHistory {

                @Test
                void loadHistory_whenNoVariable_returnsEmptyList() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentConversationHistory")).thenReturn(null);

                        List<ConversationEntry> history = stateManager.loadHistory(runtimeService, EXECUTION_ID);

                        assertTrue(history.isEmpty());
                }

                @Test
                void saveAndLoadHistory_roundTrips() {
                        List<ConversationEntry> history = List.of(ConversationEntry.user("Hello"),
                                        ConversationEntry.assistant("Hi there", List
                                                        .of(new ToolCallRequest("tc1", "tool1"))));

                        stateManager.saveHistory(runtimeService, EXECUTION_ID, history);

                        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentConversationHistory"), captor.capture());

                        String json = captor.getValue();
                        assertNotNull(json);

                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentConversationHistory")).thenReturn(json);

                        List<ConversationEntry> loaded = stateManager.loadHistory(runtimeService, EXECUTION_ID);
                        assertEquals(2, loaded.size());
                        assertEquals(Role.USER, loaded.get(0).role());
                        assertEquals("Hello", loaded.get(0).content());
                        assertEquals(Role.ASSISTANT, loaded.get(1).role());
                        assertEquals(1, loaded.get(1).toolCalls().size());
                        assertEquals("tool1", loaded.get(1).toolCalls().get(0).toolId());
                }
        }

        @Nested
        class PendingToolCalls {

                @Test
                void savePendingToolCalls_persistsToVariable() {
                        Set<String> pending = new HashSet<>(Set.of("tc1", "tc2", "tc3"));

                        stateManager.savePendingToolCalls(runtimeService, EXECUTION_ID, pending);

                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentPendingToolCalls"), anyString());
                }

                @Test
                void isPendingToolCall_returnsTrueWhenPresent() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentPendingToolCalls")).thenReturn("[\"tc1\",\"tc2\"]");

                        assertTrue(stateManager.isPendingToolCall(runtimeService, EXECUTION_ID, "tc1"));
                }

                @Test
                void isPendingToolCall_returnsFalseWhenAbsent() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentPendingToolCalls")).thenReturn("[\"tc1\"]");

                        assertFalse(stateManager.isPendingToolCall(runtimeService, EXECUTION_ID, "tc-unknown"));
                }

                @Test
                void completeToolCall_removesAndReturnsTrueWhenLastCall() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentPendingToolCalls")).thenReturn("[\"tc1\"]");

                        boolean allDone = stateManager.completeToolCall(runtimeService, EXECUTION_ID, "tc1");

                        assertTrue(allDone);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentPendingToolCalls"), anyString());
                }

                @Test
                void completeToolCall_removesAndReturnsFalseWhenMoreRemain() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentPendingToolCalls")).thenReturn("[\"tc1\",\"tc2\"]");

                        boolean allDone = stateManager.completeToolCall(runtimeService, EXECUTION_ID, "tc1");

                        assertFalse(allDone);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentPendingToolCalls"), anyString());
                }
        }

        @Nested
        class ToolResultBuffer {

                @Test
                void loadToolResultBuffer_whenNoVariable_returnsEmptyList() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer")).thenReturn(null);

                        List<ToolResult> buffer = stateManager.loadToolResultBuffer(runtimeService, EXECUTION_ID);

                        assertTrue(buffer.isEmpty());
                }

                @Test
                void appendToResultBuffer_addsToExistingBuffer() {
                        ToolResult result1 = new ToolResult("tc1", "taskA", null);
                        ToolResult result2 = new ToolResult("tc2", "taskB", null);

                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer")).thenReturn(null);
                        stateManager.appendToResultBuffer(runtimeService, EXECUTION_ID, result1);

                        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentToolResultBuffer"), captor.capture());

                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer")).thenReturn(captor.getValue());
                        stateManager.appendToResultBuffer(runtimeService, EXECUTION_ID, result2);

                        verify(runtimeService, times(2)).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentToolResultBuffer"), captor.capture());

                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer")).thenReturn(captor.getValue());

                        List<ToolResult> buffer = stateManager.loadToolResultBuffer(runtimeService, EXECUTION_ID);
                        assertEquals(2, buffer.size());
                        assertEquals("tc1", buffer.get(0).toolCallId());
                        assertEquals("tc2", buffer.get(1).toolCallId());
                }

                @Test
                void appendAllToResultBuffer_addsMultipleResults() {
                        List<ToolResult> results = List.of(ToolResult.error("tc1", "Unknown tool"),
                                        ToolResult.error("tc2", "Another error"));

                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer")).thenReturn(null);

                        stateManager.appendAllToResultBuffer(runtimeService, EXECUTION_ID, results);

                        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentToolResultBuffer"), captor.capture());

                        when(runtimeService.getVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer")).thenReturn(captor.getValue());

                        List<ToolResult> loaded = stateManager.loadToolResultBuffer(runtimeService, EXECUTION_ID);
                        assertEquals(2, loaded.size());
                        assertEquals("tc1", loaded.get(0).toolCallId());
                        assertEquals("Unknown tool", loaded.get(0).errorMessage());
                }

                @Test
                void clearToolResultBuffer_removesVariable() {
                        stateManager.clearToolResultBuffer(runtimeService, EXECUTION_ID);

                        verify(runtimeService).removeVariableLocal(EXECUTION_ID,
                                        "_agentToolResultBuffer");
                }
        }
        
        @Nested
        class LoopIndexTracking {

                @Test
                void incrementAndGetLoopIndex_firstCallReturnsOne() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentLoopIndex"))
                                        .thenReturn(null);

                        int index = stateManager.incrementAndGetLoopIndex(runtimeService, EXECUTION_ID);

                        assertEquals(1, index);
                        verify(runtimeService).setVariableLocal(EXECUTION_ID, "_agentLoopIndex", 1);
                }

                @Test
                void incrementAndGetLoopIndex_subsequentCallsIncrement() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentLoopIndex"))
                                        .thenReturn(3);

                        int index = stateManager.incrementAndGetLoopIndex(runtimeService, EXECUTION_ID);

                        assertEquals(4, index);
                }

                @Test
                void getLoopIndex_whenNotSet_returnsZero() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentLoopIndex"))
                                        .thenReturn(null);

                        assertEquals(0, stateManager.getLoopIndex(runtimeService, EXECUTION_ID));
                }
        }

        @Nested
        class TokenAccumulation {

                @Test
                void accumulateTokens_fromZero_storesValues() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentTotalPromptTokens"))
                                        .thenReturn(null);
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentTotalCompletionTokens"))
                                        .thenReturn(null);

                        stateManager.accumulateTokens(runtimeService, EXECUTION_ID, 100L, 50L);

                        verify(runtimeService).setVariableLocal(EXECUTION_ID, "_agentTotalPromptTokens", 100L);
                        verify(runtimeService).setVariableLocal(EXECUTION_ID, "_agentTotalCompletionTokens", 50L);
                }

                @Test
                void accumulateTokens_addsToExisting() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentTotalPromptTokens"))
                                        .thenReturn(200L);
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentTotalCompletionTokens"))
                                        .thenReturn(80L);

                        stateManager.accumulateTokens(runtimeService, EXECUTION_ID, 50L, 20L);

                        verify(runtimeService).setVariableLocal(EXECUTION_ID, "_agentTotalPromptTokens", 250L);
                        verify(runtimeService).setVariableLocal(EXECUTION_ID, "_agentTotalCompletionTokens", 100L);
                }

                @Test
                void getTotalPromptTokens_whenNotSet_returnsZero() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentTotalPromptTokens"))
                                        .thenReturn(null);

                        assertEquals(0L, stateManager.getTotalPromptTokens(runtimeService, EXECUTION_ID));
                }
        }

        @Nested
        class StartTimeTracking {

                @Test
                void recordAndRetrieveStartTime_roundTrips() {
                        Instant now = Instant.ofEpochMilli(1000000L);
                        stateManager.recordStartTime(runtimeService, EXECUTION_ID, now);

                        verify(runtimeService).setVariableLocal(EXECUTION_ID, "_agentStartTimeMs", 1000000L);

                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentStartTimeMs"))
                                        .thenReturn(1000000L);

                        Instant loaded = stateManager.getStartTime(runtimeService, EXECUTION_ID);
                        assertNotNull(loaded);
                        assertEquals(1000000L, loaded.toEpochMilli());
                }

                @Test
                void getStartTime_whenNotSet_returnsNull() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentStartTimeMs"))
                                        .thenReturn(null);

                        assertNull(stateManager.getStartTime(runtimeService, EXECUTION_ID));
                }
        }

        @Nested
        class ToolRequestTimeTracking {

                @Test
                void recordAndRetrieveToolRequestTime_roundTrips() {
                        Instant requestedAt = Instant.ofEpochMilli(2000000L);
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentToolRequestTimes"))
                                        .thenReturn(null);

                        stateManager.recordToolRequestTime(runtimeService, EXECUTION_ID, "tc1", requestedAt);

                        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentToolRequestTimes"), captor.capture());

                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentToolRequestTimes"))
                                        .thenReturn(captor.getValue());

                        Instant loaded = stateManager.getToolRequestTime(runtimeService, EXECUTION_ID, "tc1");
                        assertNotNull(loaded);
                        assertEquals(2000000L, loaded.toEpochMilli());
                }

                @Test
                void getToolRequestTime_whenNotRecorded_returnsNull() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentToolRequestTimes"))
                                        .thenReturn(null);

                        assertNull(stateManager.getToolRequestTime(runtimeService, EXECUTION_ID, "tc-unknown"));
                }
        }

        @Nested
        class ToolCallQueue {
                @Test
                void loadToolCallQueue_whenNoVariable_returnsEmptyList() {
                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentToolCallQueue"))
                                        .thenReturn(null);

                        List<ToolCallRequest> queue = stateManager.loadToolCallQueue(runtimeService, EXECUTION_ID);

                        assertTrue(queue.isEmpty());
                }

                @Test
                void saveAndLoadToolCallQueue_roundTrips() {
                        List<ToolCallRequest> queue = List.of(new ToolCallRequest("tc2", "tool2"),
                                        new ToolCallRequest("tc3", "tool3"));

                        stateManager.saveToolCallQueue(runtimeService, EXECUTION_ID, queue);

                        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                        verify(runtimeService).setVariableLocal(eq(EXECUTION_ID),
                                        eq("_agentToolCallQueue"), captor.capture());

                        when(runtimeService.getVariableLocal(EXECUTION_ID, "_agentToolCallQueue"))
                                        .thenReturn(captor.getValue());

                        List<ToolCallRequest> loaded = stateManager.loadToolCallQueue(runtimeService, EXECUTION_ID);
                        assertEquals(2, loaded.size());
                        assertEquals("tool2", loaded.get(0).toolId());
                        assertEquals("tool3", loaded.get(1).toolId());
                }
        }
}
