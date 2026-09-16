# Agentic Subprocess – GenAI OTEL Grafana Dashboard

> Want to run the whole stack (plugin build → engine → collector →
> Prometheus/Grafana/MLflow) locally end-to-end? See
> [`LOCAL_SETUP.md`](./LOCAL_SETUP.md).
>
> Want the full list of metrics/spans emitted and exactly how they map onto
> the OpenTelemetry GenAI semantic conventions (including known gaps)? See
> [`GENAI_SEMCONV_ALIGNMENT.md`](./GENAI_SEMCONV_ALIGNMENT.md).

Two versions of the same dashboard are provided, depending on which Grafana JSON
dashboard schema your instance uses:

- **`agentic-subprocess-otel-dashboard.json`** — Classic dashboard schema
  (`schemaVersion`, `panels`, `templating.list`). Use with **Dashboards → New →
  Import** (paste/upload JSON) on any Grafana version; Grafana auto-migrates it
  to whatever internal schema it uses.
- **`agentic-subprocess-otel-dashboard.v2.json`** — Grafana's newer **v2
  (`dashboard.grafana.app/v2beta1`) schema**, used natively by Grafana 12/13.
  This is the flattened `spec` object as shown by **Dashboard → Edit →
  Settings → JSON Model** on a v2 dashboard (no `apiVersion`/`kind`/`metadata`
  wrapper — paste only this content into that editor, or create a blank
  dashboard first and paste this in via JSON Model, then Save).

Both visualise the two instruments emitted by `AgentOtelMetrics` (agent-otel
module):

- `gen_ai.client.operation.duration` (histogram, seconds) — tagged with
  `gen_ai.operation.name` (`chat` | `execute_tool` | `invoke_agent`),
  `gen_ai.provider.name`, `gen_ai.request.model`, `gen_ai.tool.name`,
  `error.type` (tool failures only).
- `gen_ai.client.token.usage` (histogram, tokens) — tagged with
  `gen_ai.token.type` (`input` | `output`), plus provider/model.

`AgentOtelMetrics` also emits the dedicated `gen_ai.invoke_agent.duration`,
`gen_ai.invoke_agent.inference_calls`, `gen_ai.invoke_agent.tool_calls`, and
`gen_ai.execute_tool.duration` instruments alongside the two above (not
covered by these dashboards yet) — see
[`GENAI_SEMCONV_ALIGNMENT.md`](./GENAI_SEMCONV_ALIGNMENT.md) for the full list.

## Assumptions

The dashboard targets **Prometheus** (or a Prometheus-compatible store such as
Grafana Mimir), reached via an OTel Collector that scrapes/receives the OTLP
metrics from `fluxnova-engine-plugin-otel` and exposes them through the
`prometheusremotewrite`/`prometheus` exporter. Prometheus's OTLP-metric naming
convention is assumed:

- Dots become underscores: `gen_ai.client.operation.duration` → `gen_ai_client_operation_duration`.
- The unit is appended: `...duration_seconds`.
- Histograms get the usual `_bucket` / `_sum` / `_count` suffixes.
- Attribute keys also become underscored labels, e.g. `gen_ai.operation.name` → `gen_ai_operation_name`.

If your collector/exporter configuration produces different names (e.g. a
namespace prefix, or it drops the `{token}` unit from `token_usage`), use the
**`metric_prefix`** dashboard variable, or do a find/replace on the metric
names in the panel queries before importing.

## Import

**Classic (`.json`):** Grafana → Dashboards → New → Import → upload the file →
select your Prometheus datasource when prompted (`DS_PROMETHEUS` input).

**V2 (`.v2.json`):** Create/open a dashboard → Edit → Dashboard options
(gear icon) → Settings → JSON Model tab → replace the content with this
file's content → Save changes → Back to dashboard. The `datasource` variable
(kind `DatasourceVariable`, pluginId `prometheus`) lets you pick your
Prometheus instance from the dashboard's variable dropdown after import;
query-variable (`provider`/`model`) query syntax may need minor adjustment in
the variable editor UI since the raw v2 query-variable JSON shape for
Prometheus isn't fully documented publicly — if the dropdowns come up empty,
open each variable's edit dialog and re-save its query once.

## Panels

- **Overview**: call/invocation rates and tool-call error rate (stat panels).
- **Operation Duration**: p50/p95/p99 latency for `chat`, `execute_tool`
  (broken down by `gen_ai.tool.name`), and `invoke_agent`, plus an
  average-duration-by-operation comparison.
- **Token Usage**: input/output token throughput, share pie chart, and a
  breakdown table by provider/model/token type.

Variables `$provider` and `$model` filter the LLM-related panels; leave as
`All` to see everything.

## Local verification stack

The `local-stack/` folder provides a ready-to-run OTel Collector + Prometheus +
Grafana stack (via `docker compose up -d`) with the classic dashboard
auto-provisioned, plus `generate_sample_metrics.py`, a small script that emits
synthetic `gen_ai.*` metrics matching what `AgentOtelMetrics` produces, so you
can confirm the whole pipeline (and the dashboard queries) before wiring up a
real engine. See `local-stack/docker-compose.yml` for endpoints and
credentials. Point a real engine's `OTEL_EXPORTER_OTLP_ENDPOINT` at
`http://localhost:4317` instead of running the sample script to see live data.
