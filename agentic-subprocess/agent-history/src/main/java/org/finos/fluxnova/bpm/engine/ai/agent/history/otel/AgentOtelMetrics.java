package org.finos.fluxnova.bpm.engine.ai.agent.history.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import org.finos.fluxnova.bpm.engine.ai.agent.history.handler.AgentHistoryEventHandler;
import org.finos.fluxnova.bpm.engine.plugin.otel.FluxnovaOpenTelemetry;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Records agent execution metrics using the OpenTelemetry GenAI semantic conventions
 * (<a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/">gen_ai.*
 * metrics spec</a>), so any OTLP-compatible backend recognises the emitted dimensions without a
 * Fluxnova-specific vendor extension.
 *
 * <p>Two instruments are published, both attributed with {@code gen_ai.operation.name}:
 * <ul>
 *   <li>{@code gen_ai.client.token.usage} — a {@link LongHistogram} of tokens consumed,
 *       tagged with {@code gen_ai.token.type} ({@code input}/{@code output}).</li>
 *   <li>{@code gen_ai.client.operation.duration} — a {@link DoubleHistogram} of operation
 *       duration in seconds.</li>
 * </ul>
 *
 * <p>Three GenAI operations are recorded from the corresponding history events:
 * <ul>
 *   <li>{@code chat} — one LLM round-trip, from {@link AgentLlmHistoryEvent} ({@code response}).</li>
 *   <li>{@code execute_tool} — one tool activity, from {@link AgentToolCallHistoryEvent}
 *       ({@code completed}/{@code failed}).</li>
 *   <li>{@code invoke_agent} — the whole subprocess execution, from {@link
 *       AgentSubprocessHistoryEvent} ({@code end}), using the accumulated token totals.</li>
 * </ul>
 *
 * <p>In addition to the generic {@code gen_ai.client.*} instruments above, the operation-specific
 * metrics introduced by the GenAI semantic conventions are emitted alongside them (the spec
 * explicitly allows emitting both): {@code gen_ai.invoke_agent.duration}, {@code
 * gen_ai.invoke_agent.inference_calls}, {@code gen_ai.invoke_agent.tool_calls}, and {@code
 * gen_ai.execute_tool.duration}. The two {@code invoke_agent} call-count histograms are derived
 * from per-subprocess-execution counters that are incremented as {@code chat}/{@code
 * execute_tool} operations are recorded and flushed (and cleared) when the subprocess ends.</p>
 *
 * <p>Instruments are created lazily on first use rather than at construction time: {@link
 * AgentHistoryEventHandler} (and therefore this class) is typically instantiated by the Spring
 * container before the OTEL engine plugin's {@code preInit} has published the real SDK to
 * {@link FluxnovaOpenTelemetry}. Agent history events are only fired once the engine has fully
 * started, so lazy initialization guarantees the instruments are bound to the configured SDK
 * rather than permanently latched onto the no-op fallback.</p>
 */
public class AgentOtelMetrics {

    private static final String INSTRUMENTATION_SCOPE = "org.finos.fluxnova.bpm.agentic";

    private static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    private static final AttributeKey<String> REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> TOKEN_TYPE = AttributeKey.stringKey("gen_ai.token.type");
    private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> TOOL_CALL_ID = AttributeKey.stringKey("gen_ai.tool.call.id");
    private static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    private static final String TOKEN_TYPE_INPUT = "input";
    private static final String TOKEN_TYPE_OUTPUT = "output";

    private static final String OPERATION_CHAT = "chat";
    private static final String OPERATION_EXECUTE_TOOL = "execute_tool";
    private static final String OPERATION_INVOKE_AGENT = "invoke_agent";

    private volatile LongHistogram tokenUsage;
    private volatile DoubleHistogram operationDuration;
    private volatile DoubleHistogram invokeAgentDuration;
    private volatile LongHistogram invokeAgentInferenceCalls;
    private volatile LongHistogram invokeAgentToolCalls;
    private volatile DoubleHistogram executeToolDuration;

    // Per-subprocess-execution counters backing gen_ai.invoke_agent.inference_calls/tool_calls —
    // scoped to a single agent invocation and flushed (recorded + cleared) when the subprocess ends.
    private final ConcurrentMap<String, LongAdder> inferenceCallCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> toolCallCounts = new ConcurrentHashMap<>();

    public void recordLlmCall(AgentLlmHistoryEvent event) {
        Attributes attributes = attributes(OPERATION_CHAT, event.getProvider(), event.getModel());

        recordTokenUsage(attributes, event.getPromptTokens(), event.getCompletionTokens());
        recordDuration(attributes, event.getDurationMs());
        incrementCount(inferenceCallCounts, event.getSubprocessExecutionId());
    }

    public void recordToolCall(AgentToolCallHistoryEvent event) {
        String toolName = firstNonBlank(event.getToolName(), event.getToolElementId());
        boolean failed = "FAILED".equals(event.getStatus());

        AttributesBuilder builder = Attributes.builder().put(OPERATION_NAME, OPERATION_EXECUTE_TOOL);
        putIfPresent(builder, TOOL_NAME, toolName);
        putIfPresent(builder, TOOL_CALL_ID, event.getToolCallId());
        if (failed) {
            builder.put(ERROR_TYPE, "tool_error");
        }
        Attributes attributes = builder.build();

        recordDuration(attributes, event.getDurationMs());

        // gen_ai.execute_tool.duration — dedicated tool-execution metric per the GenAI semconv,
        // emitted alongside (not instead of) the generic gen_ai.client.operation.duration above.
        AttributesBuilder executeToolBuilder = Attributes.builder();
        putIfPresent(executeToolBuilder, TOOL_NAME, toolName);
        putIfPresent(executeToolBuilder, TOOL_CALL_ID, event.getToolCallId());
        putIfPresent(executeToolBuilder, AGENT_NAME, event.getSubprocessElementId());
        if (failed) {
            executeToolBuilder.put(ERROR_TYPE, "tool_error");
        }
        recordDuration(executeToolDuration(), executeToolBuilder.build(), event.getDurationMs());

        incrementCount(toolCallCounts, event.getSubprocessExecutionId());
    }

    public void recordSubprocess(AgentSubprocessHistoryEvent event) {
        if (event.getStartTime() == null || event.getEndTime() == null) {
            // Subprocess ended before it ever started (e.g. empty tool catalogue) — nothing to record.
            clearCount(inferenceCallCounts, event.getSubprocessExecutionId());
            clearCount(toolCallCounts, event.getSubprocessExecutionId());
            return;
        }

        Attributes attributes = attributes(OPERATION_INVOKE_AGENT, event.getProvider(), event.getModel());
        long durationMs = Duration.between(event.getStartTime(), event.getEndTime()).toMillis();

        recordTokenUsage(attributes, event.getTotalPromptTokens(), event.getTotalCompletionTokens());
        recordDuration(attributes, durationMs);

        // gen_ai.invoke_agent.duration/.inference_calls/.tool_calls — dedicated agent-invocation
        // metrics per the GenAI semconv, emitted alongside the generic gen_ai.client.* instruments.
        AttributesBuilder agentBuilder = Attributes.builder();
        putIfPresent(agentBuilder, AGENT_NAME, event.getSubprocessElementId());
        putIfPresent(agentBuilder, REQUEST_MODEL, event.getModel());
        Attributes agentAttributes = agentBuilder.build();

        recordDuration(invokeAgentDuration(), agentAttributes, durationMs);

        long inferenceCalls = clearCount(inferenceCallCounts, event.getSubprocessExecutionId());
        long toolCalls = clearCount(toolCallCounts, event.getSubprocessExecutionId());
        invokeAgentInferenceCalls().record(inferenceCalls, agentAttributes);
        invokeAgentToolCalls().record(toolCalls, agentAttributes);
    }

    private void recordTokenUsage(Attributes baseAttributes, long inputTokens, long outputTokens) {
        if (inputTokens > 0) {
            tokenUsage().record(inputTokens, baseAttributes.toBuilder().put(TOKEN_TYPE, TOKEN_TYPE_INPUT).build());
        }
        if (outputTokens > 0) {
            tokenUsage().record(outputTokens, baseAttributes.toBuilder().put(TOKEN_TYPE, TOKEN_TYPE_OUTPUT).build());
        }
    }

    private void recordDuration(Attributes attributes, long durationMs) {
        recordDuration(operationDuration(), attributes, durationMs);
    }

    private void recordDuration(DoubleHistogram histogram, Attributes attributes, long durationMs) {
        if (durationMs > 0) {
            histogram.record(durationMs / 1000.0, attributes);
        }
    }

    private void incrementCount(ConcurrentMap<String, LongAdder> counts, String key) {
        if (key == null) {
            return;
        }
        counts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    private long clearCount(ConcurrentMap<String, LongAdder> counts, String key) {
        if (key == null) {
            return 0L;
        }
        LongAdder adder = counts.remove(key);
        return adder == null ? 0L : adder.sum();
    }

    private Attributes attributes(String operationName, String provider, String model) {
        AttributesBuilder builder = Attributes.builder().put(OPERATION_NAME, operationName);
        putIfPresent(builder, PROVIDER_NAME, provider);
        putIfPresent(builder, REQUEST_MODEL, model);
        return builder.build();
    }

    private void putIfPresent(AttributesBuilder builder, AttributeKey<String> key, String value) {
        if (value != null && !value.isBlank()) {
            builder.put(key, value);
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private LongHistogram tokenUsage() {
        LongHistogram instrument = tokenUsage;
        if (instrument == null) {
            synchronized (this) {
                instrument = tokenUsage;
                if (instrument == null) {
                    instrument = meter().histogramBuilder("gen_ai.client.token.usage")
                            .ofLongs()
                            .setUnit("{token}")
                            .setDescription("Number of input/output tokens used per GenAI operation")
                            .build();
                    tokenUsage = instrument;
                }
            }
        }
        return instrument;
    }

    private DoubleHistogram operationDuration() {
        DoubleHistogram instrument = operationDuration;
        if (instrument == null) {
            synchronized (this) {
                instrument = operationDuration;
                if (instrument == null) {
                    instrument = meter().histogramBuilder("gen_ai.client.operation.duration")
                            .setUnit("s")
                            .setDescription("Duration of GenAI operations (LLM calls, tool calls, agent runs)")
                            .build();
                    operationDuration = instrument;
                }
            }
        }
        return instrument;
    }

    private DoubleHistogram invokeAgentDuration() {
        DoubleHistogram instrument = invokeAgentDuration;
        if (instrument == null) {
            synchronized (this) {
                instrument = invokeAgentDuration;
                if (instrument == null) {
                    instrument = meter().histogramBuilder("gen_ai.invoke_agent.duration")
                            .setUnit("s")
                            .setDescription("End-to-end duration of a single in-process agent invocation")
                            .build();
                    invokeAgentDuration = instrument;
                }
            }
        }
        return instrument;
    }

    private LongHistogram invokeAgentInferenceCalls() {
        LongHistogram instrument = invokeAgentInferenceCalls;
        if (instrument == null) {
            synchronized (this) {
                instrument = invokeAgentInferenceCalls;
                if (instrument == null) {
                    instrument = meter().histogramBuilder("gen_ai.invoke_agent.inference_calls")
                            .ofLongs()
                            .setUnit("{inference_call}")
                            .setDescription("Number of inference (model) calls a GenAI agent makes per invocation")
                            .build();
                    invokeAgentInferenceCalls = instrument;
                }
            }
        }
        return instrument;
    }

    private LongHistogram invokeAgentToolCalls() {
        LongHistogram instrument = invokeAgentToolCalls;
        if (instrument == null) {
            synchronized (this) {
                instrument = invokeAgentToolCalls;
                if (instrument == null) {
                    instrument = meter().histogramBuilder("gen_ai.invoke_agent.tool_calls")
                            .ofLongs()
                            .setUnit("{tool_call}")
                            .setDescription("Number of tool calls a GenAI agent makes per invocation")
                            .build();
                    invokeAgentToolCalls = instrument;
                }
            }
        }
        return instrument;
    }

    private DoubleHistogram executeToolDuration() {
        DoubleHistogram instrument = executeToolDuration;
        if (instrument == null) {
            synchronized (this) {
                instrument = executeToolDuration;
                if (instrument == null) {
                    instrument = meter().histogramBuilder("gen_ai.execute_tool.duration")
                            .setUnit("s")
                            .setDescription("Duration of a single tool execution")
                            .build();
                    executeToolDuration = instrument;
                }
            }
        }
        return instrument;
    }

    private Meter meter() {
        return FluxnovaOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE);
    }

}
