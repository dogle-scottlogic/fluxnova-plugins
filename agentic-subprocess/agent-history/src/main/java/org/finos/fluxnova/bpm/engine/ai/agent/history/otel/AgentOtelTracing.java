package org.finos.fluxnova.bpm.engine.ai.agent.history.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.finos.fluxnova.bpm.engine.ai.agent.history.handler.AgentHistoryEventHandler;
import org.finos.fluxnova.bpm.engine.plugin.otel.FluxnovaOpenTelemetry;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentLlmHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentSubprocessHistoryEvent;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentToolCallHistoryEvent;

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
 * invoke_agent {agent.name}             one per AgentSubprocessHistoryEvent start/end pair
 *   chat {model}                        one per AgentLlmHistoryEvent request/response pair
 *   execute_tool {tool}                 one per AgentToolCallHistoryEvent requested/completed|failed pair
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
 * trace-only consumer correlating by {@code gen_ai.conversation.id} (e.g. a test harness'
 * {@code OtelClient}) can read the exact per-run counts directly off the span, instead of
 * counting {@code chat}/{@code execute_tool} child spans, which would reintroduce the "1 chat
 * call ≈ 1 loop turn" proxy assumption the metric-side fix was specifically designed to
 * eliminate.</p>
 *
 * <p><b>Correlation and its limits.</b> {@link AgentHistoryEventHandler} observes each history
 * event independently rather than within a single call stack (LLM calls are synchronous, but
 * tool calls execute asynchronously as BPMN activities and the loop spans multiple job
 * executions). Start/end event pairs are therefore correlated using in-memory maps keyed by
 * subprocess/loop/tool-call identifiers rather than nested {@code try-with-resources} scopes.
 * This is best-effort: {@link Span} objects are in-process only, so if the engine process is
 * restarted (or a job is abandoned/retried) between the start and end event of a pair, the
 * in-flight span is silently dropped rather than exported. This mirrors the same trade-off
 * {@link AgentOtelMetrics} makes for lazy instrument initialization, and is acceptable because
 * traces are a best-effort observability signal, not the system of record — {@code
 * ACT_HI_AGENT_STEP}/{@code ACT_HI_AGENT_SUBPROCESS} remain the durable audit trail.</p>
 *
 * <p><b>Known gap.</b> {@code error.type}/{@code StatusCode.ERROR} is only ever set on {@code
 * execute_tool} spans (from {@code AgentToolCallHistoryEvent}'s {@code status}/{@code
 * errorMessage}). Neither {@link AgentSubprocessHistoryEvent} nor {@link AgentLlmHistoryEvent}
 * currently carry a failure/status field, so {@code invoke_agent} and {@code chat} spans always
 * end with {@code StatusCode.OK} even when the underlying job/LLM call ultimately errors —
 * tracking that requires plumbing a status field through the orchestrator's history events,
 * which is out of scope here.</p>
 *
 * <p><b>Content capture is opt-in.</b> {@code gen_ai.system_instructions}, {@code
 * gen_ai.input.messages}, {@code gen_ai.output.messages}, and {@code
 * gen_ai.tool.call.arguments}/{@code .result} are only set when {@link
 * AgentOtelContentCaptureProperties#isCaptureContent()} is {@code true} (default {@code false}),
 * since these carry the raw agent goal, prompts/responses, and tool inputs/outputs, which may
 * contain sensitive data. {@code gen_ai.tool.definitions} is not implemented at all — no tool
 * catalogue data flows through these history events today.</p>
 */
public class AgentOtelTracing {

    private static final String INSTRUMENTATION_SCOPE = "org.finos.fluxnova.bpm.agentic";

    private static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    private static final AttributeKey<String> REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    private static final AttributeKey<Long> USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> TOOL_CALL_ID = AttributeKey.stringKey("gen_ai.tool.call.id");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
    private static final AttributeKey<String> SYSTEM_INSTRUCTIONS =
            AttributeKey.stringKey("gen_ai.system_instructions");
    private static final AttributeKey<String> INPUT_MESSAGES = AttributeKey.stringKey("gen_ai.input.messages");
    private static final AttributeKey<String> OUTPUT_MESSAGES = AttributeKey.stringKey("gen_ai.output.messages");
    private static final AttributeKey<String> TOOL_CALL_ARGUMENTS =
            AttributeKey.stringKey("gen_ai.tool.call.arguments");
    private static final AttributeKey<String> TOOL_CALL_RESULT = AttributeKey.stringKey("gen_ai.tool.call.result");
    private static final AttributeKey<Long> INVOKE_AGENT_INFERENCE_CALLS =
            AttributeKey.longKey("gen_ai.invoke_agent.inference_calls");
    private static final AttributeKey<Long> INVOKE_AGENT_TOOL_CALLS =
            AttributeKey.longKey("gen_ai.invoke_agent.tool_calls");

    private static final String OPERATION_CHAT = "chat";
    private static final String OPERATION_EXECUTE_TOOL = "execute_tool";
    private static final String OPERATION_INVOKE_AGENT = "invoke_agent";
    private static final String STATUS_FAILED = "FAILED";

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

    public void startSubprocess(AgentSubprocessHistoryEvent event) {
        SpanBuilder builder = tracer().spanBuilder(spanName(OPERATION_INVOKE_AGENT, event.getSubprocessElementId()))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(OPERATION_NAME, OPERATION_INVOKE_AGENT)
                .setAttribute(AGENT_NAME, nullToUnknown(event.getSubprocessElementId()))
                .setAttribute(PROVIDER_NAME, nullToUnknown(event.getProvider()))
                .setAttribute(REQUEST_MODEL, nullToUnknown(event.getModel()))
                .setAttribute(CONVERSATION_ID, nullToUnknown(event.getProcessInstanceId()));
        if (captureContent()) {
            putIfPresent(builder, SYSTEM_INSTRUCTIONS, event.getGoal());
        }

        safePut(subprocessSpans, event.getSubprocessExecutionId(), builder.startSpan());
    }

    /**
     * Ends the {@code invoke_agent} span for one subprocess execution.
     *
     * @param toolCallCount the authoritative tool-call count for this execution, as recorded by
     *                      {@link AgentOtelMetrics#recordSubprocess(AgentSubprocessHistoryEvent)}
     *                      (the metric-side call MUST happen first and its return value passed
     *                      here) — set as {@code gen_ai.invoke_agent.tool_calls} on the span so a
     *                      trace-only consumer can read the exact count directly off the span
     *                      instead of counting {@code execute_tool} child spans, which would
     *                      reintroduce the "1 chat call ≈ 1 loop turn" proxy assumption this
     *                      metric was designed to eliminate.
     */
    public void endSubprocess(AgentSubprocessHistoryEvent event, long toolCallCount) {
        Span span = safeRemove(subprocessSpans, event.getSubprocessExecutionId());
        if (span == null) {
            return;
        }
        if (event.getTotalPromptTokens() > 0) {
            span.setAttribute(USAGE_INPUT_TOKENS, event.getTotalPromptTokens());
        }
        if (event.getTotalCompletionTokens() > 0) {
            span.setAttribute(USAGE_OUTPUT_TOKENS, event.getTotalCompletionTokens());
        }
        // gen_ai.invoke_agent.inference_calls/.tool_calls — the same authoritative values
        // AgentOtelMetrics records for the corresponding metrics, set directly on the span so a
        // trace-only consumer (correlating by gen_ai.conversation.id) can read the exact counts
        // without deriving them from child-span counts.
        span.setAttribute(INVOKE_AGENT_INFERENCE_CALLS, (long) event.getIterationCount());
        span.setAttribute(INVOKE_AGENT_TOOL_CALLS, toolCallCount);
        span.setStatus(StatusCode.OK);
        span.end();
    }

    public void startLlmCall(AgentLlmHistoryEvent event) {
        SpanBuilder builder = tracer().spanBuilder(OPERATION_CHAT + " " + nullToUnknown(event.getModel()))
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(OPERATION_NAME, OPERATION_CHAT)
                .setAttribute(REQUEST_MODEL, nullToUnknown(event.getModel()));
        // gen_ai.provider.name is sampling-relevant per the spec, so set it at span creation
        // when the request event already carries it, rather than waiting for the response.
        if (event.getProvider() != null && !event.getProvider().isBlank()) {
            builder.setAttribute(PROVIDER_NAME, event.getProvider());
        }
        if (captureContent()) {
            putIfPresent(builder, INPUT_MESSAGES, event.getPromptMessages());
        }
        withParent(builder, safeGet(subprocessSpans, event.getSubprocessExecutionId()));

        safePut(llmSpans, llmKey(event), builder.startSpan());
    }

    public void endLlmCall(AgentLlmHistoryEvent event) {
        Span span = safeRemove(llmSpans, llmKey(event));
        if (span == null) {
            return;
        }
        if (event.getProvider() != null && !event.getProvider().isBlank()) {
            span.setAttribute(PROVIDER_NAME, event.getProvider());
        }
        span.setAttribute(RESPONSE_MODEL, nullToUnknown(event.getModel()));
        if (event.getPromptTokens() > 0) {
            span.setAttribute(USAGE_INPUT_TOKENS, event.getPromptTokens());
        }
        if (event.getCompletionTokens() > 0) {
            span.setAttribute(USAGE_OUTPUT_TOKENS, event.getCompletionTokens());
        }
        if (captureContent()) {
            putIfPresent(span, OUTPUT_MESSAGES, event.getResponseContent());
        }
        span.setStatus(StatusCode.OK);
        span.end();
    }

    public void startToolCall(AgentToolCallHistoryEvent event) {
        String toolName = firstNonBlank(event.getToolName(), event.getToolElementId());
        SpanBuilder builder = tracer().spanBuilder(OPERATION_EXECUTE_TOOL + " " + nullToUnknown(toolName))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(OPERATION_NAME, OPERATION_EXECUTE_TOOL)
                .setAttribute(TOOL_NAME, nullToUnknown(toolName))
                .setAttribute(TOOL_CALL_ID, nullToUnknown(event.getToolCallId()));
        if (event.getSubprocessElementId() != null && !event.getSubprocessElementId().isBlank()) {
            builder.setAttribute(AGENT_NAME, event.getSubprocessElementId());
        }
        if (captureContent()) {
            putIfPresent(builder, TOOL_CALL_ARGUMENTS, event.getToolInput());
        }
        withParent(builder, safeGet(subprocessSpans, event.getSubprocessExecutionId()));

        safePut(toolSpans, event.getToolCallId(), builder.startSpan());
    }

    public void endToolCall(AgentToolCallHistoryEvent event) {
        Span span = safeRemove(toolSpans, event.getToolCallId());
        if (span == null) {
            return;
        }
        // Refresh the tool name in case it was only known once the activity resolved it.
        String toolName = firstNonBlank(event.getToolName(), event.getToolElementId());
        if (toolName != null && !toolName.isBlank()) {
            span.setAttribute(TOOL_NAME, toolName);
        }
        if (captureContent()) {
            putIfPresent(span, TOOL_CALL_RESULT, event.getToolOutput());
        }
        if (STATUS_FAILED.equals(event.getStatus())) {
            span.setAttribute(ERROR_TYPE, "tool_error");
            span.setStatus(StatusCode.ERROR, event.getErrorMessage());
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

    // ConcurrentHashMap rejects null keys/values outright (NullPointerException), but the
    // correlation keys here (execution/tool-call ids) are not always guaranteed to be populated
    // by every caller. Treat a null key as "correlation not possible" rather than propagating an
    // NPE from what is meant to be best-effort observability code.
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

    private static String llmKey(AgentLlmHistoryEvent event) {
        return event.getSubprocessExecutionId() + "|" + event.getLoopIndex();
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    // Per the GenAI semconv, the invoke_agent span name SHOULD be "invoke_agent {agent.name}"
    // when the agent name is available, and plain "invoke_agent" (no placeholder) otherwise —
    // unlike attribute values, the span name itself should not fall back to "unknown".
    private static String spanName(String operationName, String qualifier) {
        return (qualifier == null || qualifier.isBlank()) ? operationName : operationName + " " + qualifier;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private Tracer tracer() {
        return FluxnovaOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE);
    }

}
