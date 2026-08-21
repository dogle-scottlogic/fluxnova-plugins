package org.finos.fluxnova.bpm.engine.ai.agent.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import org.finos.fluxnova.bpm.engine.plugin.otel.FluxnovaOpenTelemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
 * <p>Three GenAI operations are recorded directly from the orchestrator call sites:
 * <ul>
 *   <li>{@code chat} — one LLM round-trip, recorded by
 *       {@code AgentOrchestrationJobHandler} after each response.</li>
 *   <li>{@code execute_tool} — one tool activity, recorded by
 *       {@code SubprocessToolCompletionListener} on completion/failure.</li>
 *   <li>{@code invoke_agent} — the whole subprocess execution, recorded by
 *       {@code AgentOrchestrationJobHandler} when the subprocess ends, using the accumulated
 *       token totals.</li>
 * </ul>
 *
 * <p>In addition to the generic {@code gen_ai.client.*} instruments above, the operation-specific
 * metrics introduced by the GenAI semantic conventions are emitted alongside them (the spec
 * explicitly allows emitting both): {@code gen_ai.invoke_agent.duration}, {@code
 * gen_ai.invoke_agent.inference_calls}, {@code gen_ai.invoke_agent.tool_calls}, and {@code
 * gen_ai.execute_tool.duration}. {@code gen_ai.invoke_agent.inference_calls} is recorded directly
 * from the authoritative agent-loop-turn counter already tracked by {@code AgentStateManager}
 * rather than derived from a "one chat call per loop turn" assumption. {@code
 * gen_ai.invoke_agent.tool_calls} is still derived from a per-subprocess-execution counter
 * incremented as {@code execute_tool} operations are recorded and flushed (and cleared) when the
 * subprocess ends.</p>
 *
 * <p>Instruments are created lazily on first use rather than at construction time: this bean is
 * typically instantiated by Spring before the OTEL engine plugin's {@code preInit} has published
 * the real SDK to {@link FluxnovaOpenTelemetry}. The direct orchestrator/tool-listener calls only
 * happen once the engine has fully started, so lazy initialization guarantees the instruments are
 * bound to the configured SDK rather than permanently latched onto the no-op fallback.</p>
 */
public class AgentOtelMetrics {

    private static final String INSTRUMENTATION_SCOPE = "org.finos.fluxnova.bpm.agentic";

    private static final AttributeKey<String> OPERATION_NAME =
            AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> PROVIDER_NAME =
            AttributeKey.stringKey("gen_ai.provider.name");
    private static final AttributeKey<String> REQUEST_MODEL =
            AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> TOKEN_TYPE =
            AttributeKey.stringKey("gen_ai.token.type");
    private static final AttributeKey<String> TOOL_NAME =
            AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> TOOL_CALL_ID =
            AttributeKey.stringKey("gen_ai.tool.call.id");
    private static final AttributeKey<String> AGENT_NAME =
            AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> ERROR_TYPE =
            AttributeKey.stringKey("error.type");

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

    // Per-subprocess-execution counter backing gen_ai.invoke_agent.tool_calls — scoped to a
    // single agent invocation and flushed (recorded + cleared) when the subprocess ends.
    private final ConcurrentMap<String, LongAdder> toolCallCounts = new ConcurrentHashMap<>();

    public void recordLlmCall(String provider, String model, long promptTokens,
            long completionTokens, long durationMs) {
        Attributes attributes = attributes(OPERATION_CHAT, provider, model);

        recordTokenUsage(attributes, promptTokens, completionTokens);
        recordDuration(attributes, durationMs);
    }

    public void recordToolCall(String subprocessExecutionId, String subprocessElementId,
            String toolCallId, String toolElementId, String toolName, boolean failed,
            long durationMs) {
        String effectiveToolName = firstNonBlank(toolName, toolElementId);

        AttributesBuilder builder = Attributes.builder().put(OPERATION_NAME, OPERATION_EXECUTE_TOOL);
        putIfPresent(builder, TOOL_NAME, effectiveToolName);
        putIfPresent(builder, TOOL_CALL_ID, toolCallId);
        if (failed) {
            builder.put(ERROR_TYPE, "tool_error");
        }
        Attributes attributes = builder.build();

        recordDuration(attributes, durationMs);

        AttributesBuilder executeToolBuilder = Attributes.builder();
        putIfPresent(executeToolBuilder, TOOL_NAME, effectiveToolName);
        putIfPresent(executeToolBuilder, TOOL_CALL_ID, toolCallId);
        putIfPresent(executeToolBuilder, AGENT_NAME, subprocessElementId);
        if (failed) {
            executeToolBuilder.put(ERROR_TYPE, "tool_error");
        }
        recordDuration(executeToolDuration(), executeToolBuilder.build(), durationMs);

        incrementCount(toolCallCounts, subprocessExecutionId);
    }

    /**
     * Records the {@code invoke_agent} metrics for one subprocess execution.
     *
     * @return the authoritative tool-call count recorded for this execution (0 if the
     *         subprocess ended before it ever started, or if no tool calls were made), so
     *         callers such as {@code AgentOrchestrationJobHandler} can pass the exact same value
     *         on to {@link AgentOtelTracing#endSubprocess(String, long, long, int, long)} —
     *         keeping the {@code invoke_agent} span's {@code gen_ai.invoke_agent.tool_calls}
     *         attribute consistent with this metric rather than re-derived independently.
     */
    public long recordSubprocess(String subprocessExecutionId, String subprocessElementId,
            String provider, String model, int iterationCount, Instant startTime, Instant endTime,
            long totalPromptTokens, long totalCompletionTokens) {
        if (startTime == null || endTime == null) {
            return clearCount(toolCallCounts, subprocessExecutionId);
        }

        Attributes attributes = attributes(OPERATION_INVOKE_AGENT, provider, model);
        long durationMs = Duration.between(startTime, endTime).toMillis();

        recordTokenUsage(attributes, totalPromptTokens, totalCompletionTokens);
        recordDuration(attributes, durationMs);

        AttributesBuilder agentBuilder = Attributes.builder();
        putIfPresent(agentBuilder, AGENT_NAME, subprocessElementId);
        putIfPresent(agentBuilder, REQUEST_MODEL, model);
        Attributes agentAttributes = agentBuilder.build();

        recordDuration(invokeAgentDuration(), agentAttributes, durationMs);

        long toolCalls = clearCount(toolCallCounts, subprocessExecutionId);
        invokeAgentInferenceCalls().record(iterationCount, agentAttributes);
        invokeAgentToolCalls().record(toolCalls, agentAttributes);
        return toolCalls;
    }

    private void recordTokenUsage(Attributes baseAttributes, long inputTokens, long outputTokens) {
        if (inputTokens > 0) {
            tokenUsage().record(inputTokens,
                    baseAttributes.toBuilder().put(TOKEN_TYPE, TOKEN_TYPE_INPUT).build());
        }
        if (outputTokens > 0) {
            tokenUsage().record(outputTokens,
                    baseAttributes.toBuilder().put(TOKEN_TYPE, TOKEN_TYPE_OUTPUT).build());
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
        counts.computeIfAbsent(key, ignored -> new LongAdder()).increment();
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

    // Six independent instruments each need identical create-on-first-use double-checked-locking
    // (see class javadoc for why lazy init is required), so that boilerplate is centralized here
    // rather than repeated per-instrument.
    private synchronized <T> T lazyInstrument(Supplier<T> currentValue, Consumer<T> assign,
            Supplier<T> factory) {
        T instrument = currentValue.get();
        if (instrument == null) {
            instrument = factory.get();
            assign.accept(instrument);
        }
        return instrument;
    }

    private LongHistogram tokenUsage() {
        LongHistogram instrument = tokenUsage;
        return instrument != null ? instrument
                : lazyInstrument(() -> tokenUsage, v -> tokenUsage = v,
                        () -> meter().histogramBuilder("gen_ai.client.token.usage")
                                .ofLongs()
                                .setUnit("{token}")
                                .setDescription("Number of input/output tokens used per GenAI operation")
                                .build());
    }

    private DoubleHistogram operationDuration() {
        DoubleHistogram instrument = operationDuration;
        return instrument != null ? instrument
                : lazyInstrument(() -> operationDuration, v -> operationDuration = v,
                        () -> meter().histogramBuilder("gen_ai.client.operation.duration")
                                .setUnit("s")
                                .setDescription(
                                        "Duration of GenAI operations (LLM calls, tool calls, agent runs)")
                                .build());
    }

    private DoubleHistogram invokeAgentDuration() {
        DoubleHistogram instrument = invokeAgentDuration;
        return instrument != null ? instrument
                : lazyInstrument(() -> invokeAgentDuration, v -> invokeAgentDuration = v,
                        () -> meter().histogramBuilder("gen_ai.invoke_agent.duration")
                                .setUnit("s")
                                .setDescription("End-to-end duration of a single in-process agent invocation")
                                .build());
    }

    private LongHistogram invokeAgentInferenceCalls() {
        LongHistogram instrument = invokeAgentInferenceCalls;
        return instrument != null ? instrument
                : lazyInstrument(() -> invokeAgentInferenceCalls, v -> invokeAgentInferenceCalls = v,
                        () -> meter().histogramBuilder("gen_ai.invoke_agent.inference_calls")
                                .ofLongs()
                                .setUnit("{inference_call}")
                                .setDescription(
                                        "Number of inference (model) calls a GenAI agent makes per invocation")
                                .build());
    }

    private LongHistogram invokeAgentToolCalls() {
        LongHistogram instrument = invokeAgentToolCalls;
        return instrument != null ? instrument
                : lazyInstrument(() -> invokeAgentToolCalls, v -> invokeAgentToolCalls = v,
                        () -> meter().histogramBuilder("gen_ai.invoke_agent.tool_calls")
                                .ofLongs()
                                .setUnit("{tool_call}")
                                .setDescription("Number of tool calls a GenAI agent makes per invocation")
                                .build());
    }

    private DoubleHistogram executeToolDuration() {
        DoubleHistogram instrument = executeToolDuration;
        return instrument != null ? instrument
                : lazyInstrument(() -> executeToolDuration, v -> executeToolDuration = v,
                        () -> meter().histogramBuilder("gen_ai.execute_tool.duration")
                                .setUnit("s")
                                .setDescription("Duration of a single tool execution")
                                .build());
    }

    private Meter meter() {
        return FluxnovaOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE);
    }
}
