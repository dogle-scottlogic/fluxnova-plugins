package org.finos.fluxnova.bpm.engine.ai.agent.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.finos.fluxnova.bpm.engine.plugin.otel.FluxnovaOpenTelemetry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Emits OpenTelemetry spans attributed with the OpenTelemetry GenAI semantic conventions
 * (<a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/">gen_ai.* spans
 * spec</a>) for agent subprocess executions — the same attribute names MLflow emits when
 * exporting in GenAI semconv format, so any OTLP-compatible backend renders the trace natively.
 *
 * <p>One span tree is produced per subprocess execution:
 * <pre>
 * invoke_agent {agent.name}             one per subprocess start/end pair
 *   chat {model}                        one per LLM request/response pair
 *   execute_tool {tool}                 one per tool requested/completed|failed pair
 *   chat {model}
 *   ...
 * </pre>
 *
 * <p>The {@code invoke_agent} span name uses {@code gen_ai.agent.name} (the BPMN ad-hoc
 * subprocess element id) rather than the model, per the span-naming guidance in the spec
 * (falling back to plain {@code invoke_agent} when unavailable); {@code execute_tool} spans also
 * carry {@code gen_ai.agent.name} for the executing agent when known.</p>
 *
 * <p>The {@code invoke_agent} span also carries {@code gen_ai.invoke_agent.inference_calls} and
 * {@code gen_ai.invoke_agent.tool_calls} attributes (set at span end), using the same
 * authoritative values {@link AgentOtelMetrics} records for the corresponding metrics — so a
 * trace-only consumer correlating by {@code gen_ai.conversation.id} can read the exact per-run
 * counts directly off the span, instead of counting {@code chat}/{@code execute_tool} child
 * spans, which would reintroduce the "1 chat call ≈ 1 loop turn" proxy assumption the
 * metric-side fix was specifically designed to eliminate.</p>
 *
 * <p><b>Correlation and its limits.</b> {@code AgentOrchestrationJobHandler} and
 * {@code SubprocessToolCompletionListener} invoke these methods across multiple independent call
 * sites (LLM calls are synchronous, but tool calls execute asynchronously as BPMN activities and
 * the loop spans multiple job executions). Start/end pairs are therefore correlated using
 * in-memory maps keyed by subprocess/loop/tool-call identifiers rather than nested
 * {@code try-with-resources} scopes. This is best-effort: {@link Span} objects are in-process
 * only, so if the engine process is restarted (or a job is abandoned/retried) between the start
 * and end event of a pair, the in-flight span is silently dropped rather than exported.</p>
 *
 * <p><b>Known gap.</b> {@code error.type}/{@code StatusCode.ERROR} is only ever set on {@code
 * execute_tool} spans. The direct subprocess/LLM call sites currently carry no failure/status
 * field, so {@code invoke_agent} and {@code chat} spans always end with {@code StatusCode.OK}
 * even when the underlying job/LLM call ultimately errors.</p>
 *
 * <p><b>Content capture is opt-in.</b> {@code gen_ai.system_instructions}, {@code
 * gen_ai.input.messages}, {@code gen_ai.output.messages}, and {@code
 * gen_ai.tool.call.arguments}/{@code .result} are only set when {@link
 * AgentOtelContentCaptureProperties#isCaptureContent()} is {@code true} (default {@code false}),
 * since these carry the raw agent goal, prompts/responses, and tool inputs/outputs, which may
 * contain sensitive data.</p>
 */
public class AgentOtelTracing {

    private static final String INSTRUMENTATION_SCOPE = "org.finos.fluxnova.bpm.agentic";

    private static final AttributeKey<String> OPERATION_NAME =
            AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> PROVIDER_NAME =
            AttributeKey.stringKey("gen_ai.provider.name");
    private static final AttributeKey<String> REQUEST_MODEL =
            AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> RESPONSE_MODEL =
            AttributeKey.stringKey("gen_ai.response.model");
    private static final AttributeKey<Long> USAGE_INPUT_TOKENS =
            AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> USAGE_OUTPUT_TOKENS =
            AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<String> TOOL_NAME =
            AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> TOOL_CALL_ID =
            AttributeKey.stringKey("gen_ai.tool.call.id");
    private static final AttributeKey<String> ERROR_TYPE =
            AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> AGENT_NAME =
            AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> CONVERSATION_ID =
            AttributeKey.stringKey("gen_ai.conversation.id");
    private static final AttributeKey<String> SYSTEM_INSTRUCTIONS =
            AttributeKey.stringKey("gen_ai.system_instructions");
    private static final AttributeKey<String> INPUT_MESSAGES =
            AttributeKey.stringKey("gen_ai.input.messages");
    private static final AttributeKey<String> OUTPUT_MESSAGES =
            AttributeKey.stringKey("gen_ai.output.messages");
    private static final AttributeKey<String> TOOL_CALL_ARGUMENTS =
            AttributeKey.stringKey("gen_ai.tool.call.arguments");
    private static final AttributeKey<String> TOOL_CALL_RESULT =
            AttributeKey.stringKey("gen_ai.tool.call.result");
    private static final AttributeKey<Long> INVOKE_AGENT_INFERENCE_CALLS =
            AttributeKey.longKey("gen_ai.invoke_agent.inference_calls");
    private static final AttributeKey<Long> INVOKE_AGENT_TOOL_CALLS =
            AttributeKey.longKey("gen_ai.invoke_agent.tool_calls");

    private static final String OPERATION_CHAT = "chat";
    private static final String OPERATION_EXECUTE_TOOL = "execute_tool";
    private static final String OPERATION_INVOKE_AGENT = "invoke_agent";

    private final ConcurrentMap<String, Span> subprocessSpans = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Span> llmSpans = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Span> toolSpans = new ConcurrentHashMap<>();
    private final AgentOtelContentCaptureProperties contentCaptureProperties;

    public AgentOtelTracing() {
        this(new AgentOtelContentCaptureProperties());
    }

    public AgentOtelTracing(AgentOtelContentCaptureProperties contentCaptureProperties) {
        this.contentCaptureProperties = contentCaptureProperties;
    }

    public void startSubprocess(String subprocessExecutionId, String subprocessElementId,
            String processInstanceId, String provider, String model, String goal,
            String inputVariables) {
        SpanBuilder builder = tracer().spanBuilder(spanName(OPERATION_INVOKE_AGENT, subprocessElementId))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(OPERATION_NAME, OPERATION_INVOKE_AGENT)
                .setAttribute(AGENT_NAME, nullToUnknown(subprocessElementId))
                .setAttribute(PROVIDER_NAME, nullToUnknown(provider))
                .setAttribute(REQUEST_MODEL, nullToUnknown(model))
                .setAttribute(CONVERSATION_ID, nullToUnknown(processInstanceId));
        if (captureContent()) {
            putIfPresent(builder, SYSTEM_INSTRUCTIONS, goal);
            putIfPresent(builder, SYSTEM_INSTRUCTIONS, goal);
            putIfPresent(builder, INPUT_MESSAGES, inputVariables);
        }
        safePut(subprocessSpans, subprocessExecutionId, builder.startSpan());
    }

    /**
     * Ends the {@code invoke_agent} span for one subprocess execution.
     *
     * @param toolCallCount the authoritative tool-call count for this execution, as recorded by
     *                      {@link AgentOtelMetrics#recordSubprocess(String, String, String, String,
     *                      int, java.time.Instant, java.time.Instant, long, long)} (the
     *                      metric-side call MUST happen first and its return value passed here).
     * @param finalOutput   the agent's final assistant response text, if one was produced before
     *                      termination (e.g. {@code null} when the subprocess was terminated
     *                      before any LLM call completed, such as an empty tool catalogue).
     *                      Recorded as the span's {@code gen_ai.output.messages} attribute, gated
     *                      behind content-capture, so backends that derive a span's
     *                      "response"/output purely from that attribute (e.g. MLflow's GenAI
     *                      semconv trace ingestion) can render it for this invoke_agent span too.
     */
    public void endSubprocess(String subprocessExecutionId, long totalPromptTokens,
            long totalCompletionTokens, int iterationCount, long toolCallCount,
            String finalOutput) {
        Span span = safeRemove(subprocessSpans, subprocessExecutionId);
        if (span == null) {
            return;
        }
        if (totalPromptTokens > 0) {
            span.setAttribute(USAGE_INPUT_TOKENS, totalPromptTokens);
        }
        if (totalCompletionTokens > 0) {
            span.setAttribute(USAGE_OUTPUT_TOKENS, totalCompletionTokens);
        }
        span.setAttribute(INVOKE_AGENT_INFERENCE_CALLS, (long) iterationCount);
        span.setAttribute(INVOKE_AGENT_TOOL_CALLS, toolCallCount);
        if (captureContent()) {
            putIfPresent(span, OUTPUT_MESSAGES, finalOutput);
        }
        span.setStatus(StatusCode.OK);
        span.end();
    }

    public void startLlmCall(String subprocessExecutionId, int loopIndex, String provider,
            String model, String promptMessages) {
        SpanBuilder builder = tracer().spanBuilder(OPERATION_CHAT + " " + nullToUnknown(model))
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(OPERATION_NAME, OPERATION_CHAT)
                .setAttribute(REQUEST_MODEL, nullToUnknown(model));
        if (provider != null && !provider.isBlank()) {
            builder.setAttribute(PROVIDER_NAME, provider);
        }
        if (captureContent()) {
            putIfPresent(builder, INPUT_MESSAGES, promptMessages);
        }
        withParent(builder, safeGet(subprocessSpans, subprocessExecutionId));

        safePut(llmSpans, llmKey(subprocessExecutionId, loopIndex), builder.startSpan());
    }

    public void endLlmCall(String subprocessExecutionId, int loopIndex, String provider,
            String model, long promptTokens, long completionTokens, String responseContent) {
        Span span = safeRemove(llmSpans, llmKey(subprocessExecutionId, loopIndex));
        if (span == null) {
            return;
        }
        if (provider != null && !provider.isBlank()) {
            span.setAttribute(PROVIDER_NAME, provider);
        }
        span.setAttribute(RESPONSE_MODEL, nullToUnknown(model));
        if (promptTokens > 0) {
            span.setAttribute(USAGE_INPUT_TOKENS, promptTokens);
        }
        if (completionTokens > 0) {
            span.setAttribute(USAGE_OUTPUT_TOKENS, completionTokens);
        }
        if (captureContent()) {
            putIfPresent(span, OUTPUT_MESSAGES, responseContent);
        }
        span.setStatus(StatusCode.OK);
        span.end();
    }

    public void startToolCall(String subprocessExecutionId, String subprocessElementId,
            String toolCallId, String toolElementId, String toolName, String toolInput) {
        String effectiveToolName = firstNonBlank(toolName, toolElementId);
        SpanBuilder builder =
                tracer().spanBuilder(OPERATION_EXECUTE_TOOL + " " + nullToUnknown(effectiveToolName))
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute(OPERATION_NAME, OPERATION_EXECUTE_TOOL)
                        .setAttribute(TOOL_NAME, nullToUnknown(effectiveToolName))
                        .setAttribute(TOOL_CALL_ID, nullToUnknown(toolCallId));
        if (subprocessElementId != null && !subprocessElementId.isBlank()) {
            builder.setAttribute(AGENT_NAME, subprocessElementId);
        }
        if (captureContent()) {
            putIfPresent(builder, TOOL_CALL_ARGUMENTS, toolInput);
        }
        withParent(builder, safeGet(subprocessSpans, subprocessExecutionId));

        safePut(toolSpans, toolCallId, builder.startSpan());
    }

    public void endToolCall(String toolCallId, String toolElementId, String toolName,
            boolean failed, String errorMessage, String toolOutput) {
        Span span = safeRemove(toolSpans, toolCallId);
        if (span == null) {
            return;
        }
        String effectiveToolName = firstNonBlank(toolName, toolElementId);
        if (effectiveToolName != null && !effectiveToolName.isBlank()) {
            span.setAttribute(TOOL_NAME, effectiveToolName);
        }
        if (captureContent()) {
            putIfPresent(span, TOOL_CALL_RESULT, toolOutput);
        }
        if (failed) {
            span.setAttribute(ERROR_TYPE, "tool_error");
            span.setStatus(StatusCode.ERROR, errorMessage);
        } else {
            span.setStatus(StatusCode.OK);
        }
        span.end();
    }

    private static void withParent(SpanBuilder builder, Span parent) {
        if (parent != null) {
            builder.setParent(Context.current().with(parent));
        }
    }

    private boolean captureContent() {
        return contentCaptureProperties.isCaptureContent();
    }

    private static void putIfPresent(SpanBuilder builder, AttributeKey<String> key, String value) {
        if (value != null && !value.isBlank()) {
            builder.setAttribute(key, value);
        }
    }

    private static void putIfPresent(Span span, AttributeKey<String> key, String value) {
        if (value != null && !value.isBlank()) {
            span.setAttribute(key, value);
        }
    }

    private static void safePut(ConcurrentMap<String, Span> map, String key, Span span) {
        if (key == null) {
            span.end();
            return;
        }
        map.put(key, span);
    }

    private static Span safeGet(ConcurrentMap<String, Span> map, String key) {
        return key == null ? null : map.get(key);
    }

    private static Span safeRemove(ConcurrentMap<String, Span> map, String key) {
        return key == null ? null : map.remove(key);
    }

    private static String llmKey(String subprocessExecutionId, int loopIndex) {
        return subprocessExecutionId + "|" + loopIndex;
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String spanName(String operationName, String qualifier) {
        return (qualifier == null || qualifier.isBlank()) ? operationName
                : operationName + " " + qualifier;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private Tracer tracer() {
        return FluxnovaOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE);
    }
}
