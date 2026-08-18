# Agentic Subprocess — GenAI OpenTelemetry Metrics & Traces

This document describes every OpenTelemetry signal emitted by the
`agentic-subprocess` module (produced by `AgentOtelMetrics` and
`AgentOtelTracing` in `agent-history/.../history/otel/`), and how each one maps onto
the [OpenTelemetry GenAI semantic conventions][semconv] (the conventions live in [
`open-telemetry/semantic-conventions-genai`][semconv-repo]
— they moved out of the main `opentelemetry-specification`/`semantic-conventions`
repos into their own).

[semconv]: https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/README.md
[semconv-repo]: https://github.com/open-telemetry/semantic-conventions-genai

All instruments/spans use the `gen_ai.*` namespace and instrumentation scope
`org.finos.fluxnova.bpm.agentic`, so any OTLP-compatible backend (Prometheus, Grafana, MLflow, Datadog, etc.) recognises
them without a Fluxnova-specific vendor extension.

## Source → signal mapping

One BPMN ad-hoc agent subprocess execution produces the following event stream (from `AgentHistoryEventHandler`), each
of which drives both a metric and/or a span:

| History event                                                   | GenAI operation | Metric(s)                                                                                                                                                                | Span                           |
|-----------------------------------------------------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| `AgentSubprocessHistoryEvent` (`start`/`end`)                   | `invoke_agent`  | `gen_ai.client.token.usage`, `gen_ai.client.operation.duration`, `gen_ai.invoke_agent.duration`, `gen_ai.invoke_agent.inference_calls`, `gen_ai.invoke_agent.tool_calls` | `gen_ai.invoke_agent.internal` |
| `AgentLlmHistoryEvent` (`request`/`response`)                   | `chat`          | `gen_ai.client.token.usage`, `gen_ai.client.operation.duration`                                                                                                          | `gen_ai.inference.client`      |
| `AgentToolCallHistoryEvent` (`requested`/`completed`\|`failed`) | `execute_tool`  | `gen_ai.client.operation.duration`, `gen_ai.execute_tool.duration`                                                                                                       | `gen_ai.execute_tool.internal` |

Span tree per subprocess execution:

```
invoke_agent {gen_ai.agent.name}          AgentSubprocessHistoryEvent start/end
  chat {gen_ai.request.model}             AgentLlmHistoryEvent request/response
  execute_tool {gen_ai.tool.name}         AgentToolCallHistoryEvent requested/completed|failed
  chat {gen_ai.request.model}
  ...
```

## Metrics

### `gen_ai.client.token.usage`

- **Instrument:** histogram (long), unit `{token}`.
- **Recorded:** once per `chat` (`AgentLlmHistoryEvent` response) and once per
  `invoke_agent` (`AgentSubprocessHistoryEvent` end, using accumulated totals)
  — one data point for input tokens and one for output tokens, each time.
- **Attributes:** `gen_ai.operation.name` (`chat`\|`invoke_agent`),
  `gen_ai.token.type` (`input`\|`output`), `gen_ai.provider.name`,
  `gen_ai.request.model` (all when available).
- **Spec alignment:** ✅ exact name/unit/instrument match with
  [`metrics.yaml`](https://github.com/open-telemetry/semantic-conventions-genai/blob/main/model/gen-ai/metrics.yaml).

### `gen_ai.client.operation.duration`

- **Instrument:** histogram (double), unit `s`.
- **Recorded:** once per `chat`, `execute_tool`, and `invoke_agent` operation, as the generic "one metric covers every
  GenAI operation type" instrument.
- **Attributes:** `gen_ai.operation.name`, `gen_ai.provider.name`,
  `gen_ai.request.model`, `gen_ai.tool.name`/`gen_ai.tool.call.id` (tool calls only), `error.type` (`tool_error`, tool
  failures only).
- **Spec alignment:** ✅ exact name/unit/instrument match.

### `gen_ai.invoke_agent.duration` *(new)*

- **Instrument:** histogram (double), unit `s`.
- **Recorded:** once per subprocess execution (`AgentSubprocessHistoryEvent`
  end), value equal to the `gen_ai.client.operation.duration` value recorded for the same `invoke_agent` operation (per
  the spec's guidance that the two SHOULD match when both are emitted alongside an `invoke_agent.internal` span).
- **Attributes:** `gen_ai.agent.name`, `gen_ai.request.model`.
- **Spec alignment:** ✅ added to align with the dedicated agent-invocation metric the spec introduced alongside (not
  instead of) the generic
  `gen_ai.client.operation.duration`.

### `gen_ai.invoke_agent.inference_calls` / `gen_ai.invoke_agent.tool_calls` *(new)*

- **Instrument:** histograms (long), units `{inference_call}` / `{tool_call}`.
- **Recorded:** once per subprocess execution, with the count of `chat` /
  `execute_tool` operations that occurred inside that invocation (tracked via per-`subprocessExecutionId` counters that
  are incremented as each call is recorded and flushed — recorded + cleared — when the subprocess ends).
- **Attributes:** `gen_ai.agent.name`.
- **Spec alignment:** ✅ added; matches the spec's per-invocation call-count metrics.

### `gen_ai.execute_tool.duration` *(new)*

- **Instrument:** histogram (double), unit `s`.
- **Recorded:** once per tool call (`AgentToolCallHistoryEvent`
  `completed`/`failed`), alongside the generic
  `gen_ai.client.operation.duration` recorded for the same event.
- **Attributes:** `gen_ai.tool.name`, `gen_ai.tool.call.id`,
  `gen_ai.agent.name` (the executing agent, when known), `error.type`
  (`tool_error`, tool failures only).
- **Spec alignment:** ✅ added; matches the spec's dedicated tool-execution duration metric.

### Not implemented (and why)

| Metric                                                                               | Reason                                                                                                                                               |
|--------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `gen_ai.client.operation.time_to_first_chunk` / `.time_per_output_chunk`             | Streaming-only metrics; LLM calls here are synchronous request/response, not streamed.                                                               |
| `gen_ai.server.request.duration` / `.time_per_output_token` / `.time_to_first_token` | Server-side (model-host) metrics — this module instruments the client/orchestrator side only.                                                        |
| `gen_ai.invoke_workflow.duration`                                                    | No `invoke_workflow`-level concept exists in this module; each ad-hoc subprocess is modelled as a single `invoke_agent`, not a multi-agent workflow. |

## Traces

### `invoke_agent` span (type `gen_ai.invoke_agent.internal`)

- **Kind:** `INTERNAL` (correct — the agent loop runs in-process, not over a remote agent API).
- **Name:** `invoke_agent {gen_ai.agent.name}` (falls back to plain
  `invoke_agent` if the agent name is unavailable), per the spec's span-naming guidance. Uses the BPMN ad-hoc subprocess
  element id as `gen_ai.agent.name`
  — **not** the model, which was the naming used before this alignment pass.
- **Attributes:** `gen_ai.operation.name`, `gen_ai.agent.name`,
  `gen_ai.provider.name`, `gen_ai.request.model`, `gen_ai.conversation.id`
  (the process instance id), `gen_ai.usage.input_tokens`/`output_tokens`
  (accumulated totals, set at span end).
- **Lifecycle:** started on `AgentSubprocessHistoryEvent` `start`, ended on
  `end`.

### `chat` span (type `gen_ai.inference.client`)

- **Kind:** `CLIENT` (correct — this represents a call to an LLM provider).
- **Name:** `chat {gen_ai.request.model}`.
- **Attributes:** `gen_ai.operation.name`, `gen_ai.request.model`,
  `gen_ai.response.model`, `gen_ai.provider.name` (set at span creation when already known from the request, refreshed
  at span end),
  `gen_ai.usage.input_tokens`/`output_tokens`.
- **Lifecycle:** started on `AgentLlmHistoryEvent` `request`, ended on
  `response`. Correlated by `subprocessExecutionId|loopIndex` since LLM calls are synchronous within a single job
  execution.

### `execute_tool` span (type `gen_ai.execute_tool.internal`)

- **Kind:** `INTERNAL` (correct — tools here are BPMN ad-hoc activities executed in-process, not a remote tool-execution
  service).
- **Name:** `execute_tool {gen_ai.tool.name}`.
- **Attributes:** `gen_ai.operation.name`, `gen_ai.tool.name`,
  `gen_ai.tool.call.id`, `gen_ai.agent.name` (the executing agent, when known), `error.type` (`tool_error`, on failure).
- **Lifecycle:** started on `AgentToolCallHistoryEvent` `requested`, ended on
  `completed`/`failed`. Correlated by `toolCallId` since tool activities execute asynchronously (across separate job
  executions) relative to the event handler.

### Known gaps

- **No failure signalling above the tool level.** `error.type` /
  `StatusCode.ERROR` is only ever set on `execute_tool` spans/metrics, from
  `AgentToolCallHistoryEvent`'s `status`/`errorMessage` fields.
  `AgentSubprocessHistoryEvent` and `AgentLlmHistoryEvent` carry no status/error field, so `invoke_agent` and `chat`
  spans always end with
  `StatusCode.OK` even if the underlying job ultimately fails/retries. Closing this gap would require plumbing a
  status/error field through
  `AgentOrchestrationJobHandler` (wrapping `llmService.call(...)` and the subprocess-termination path) and the
  corresponding history events — tracked as follow-up work, not implemented here.
- **Span leak on unhandled LLM exceptions.** Because `llmService.call(...)`
  is not wrapped in a try/catch in `AgentOrchestrationJobHandler`, an exception there means no `agent-llm:response`
  event is ever fired, so the in-flight `chat` span started on `agent-llm:request` is never closed or exported. This is
  a consequence of the same gap above (no error event to react to) rather than a bug in `AgentOtelTracing` itself.
- **Content capture (`gen_ai.input.messages`, `gen_ai.output.messages`,
  `gen_ai.system_instructions`, `gen_ai.tool.definitions`) is opt-in per the spec** and intentionally not implemented,
  to avoid emitting potentially-sensitive prompt/response content by default.

## Dashboard

See [`README.md`](./README.md) for the Grafana dashboards (`agentic-subprocess-otel-dashboard.json` / `.v2.json`) that
visualise the
`gen_ai.client.*` instruments, and [`LOCAL_SETUP.md`](./LOCAL_SETUP.md) for a local Prometheus/Grafana/MLflow stack to
verify all of the above end-to-end.
