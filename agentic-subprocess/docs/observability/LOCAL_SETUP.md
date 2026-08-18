# Running the Agentic Subprocess Observability Stack Locally

End-to-end guide to build the plugin, run it inside a local Fluxnova BPM Run
instance, and view the resulting GenAI **metrics** (Prometheus/Grafana) and
**traces** (MLflow) it emits.

Assumes:

- Windows + PowerShell.
- `fluxnova-plugins` (this repo) and `fluxnova-bpm-platform` cloned as
  siblings, e.g. `C:\dev\fluxnova-plugins` and `C:\dev\fluxnova-bpm-platform`.
- JDK 21, Maven, and Python 3.9+ (for MLflow) available on `PATH`.

## 1. Build the plugin (`fluxnova-plugins`)

```powershell
cd C:\dev\fluxnova-plugins
mvn clean install -DskipTests
```

This installs `org.finos.fluxnova.bpm:fluxnova-engine-plugins-ai-agentic-subprocess`
(and its submodules — `agent-config`, `agent-history`, `agent-llm-connector`,
`agent-orchestrator`, etc.) into your local `~/.m2` repository. `agent-history`
is the module that contains `AgentOtelMetrics`/`AgentOtelTracing` — the
instrumentation being verified.

## 2. Build the Fluxnova BPM platform (`fluxnova-bpm-platform`)

```powershell
cd C:\dev\fluxnova-bpm-platform
.\mvnw.cmd clean install -DskipTests -DskipITs
```

`engine/pom.xml` depends on the `fluxnova-engine-plugins-ai-agentic-subprocess`
artifact built in step 1, so **step 1 must run first** (and after any change
to the plugin code). This produces the distro zip:

```
distro\run\distro\target\fluxnova-bpm-run-3.1.0-SNAPSHOT.zip
```

which already bundles the agentic-subprocess jars.

## 3. Extract the distro to `local/` and wire up the OTel plugin

```powershell
Expand-Archive -Force `
  C:\dev\fluxnova-bpm-platform\distro\run\distro\target\fluxnova-bpm-run-3.1.0-SNAPSHOT.zip `
  C:\dev\fluxnova-bpm-platform\local
```

> ⚠️ **This overwrites `local\configuration\default.yml` back to its
> pristine state every time.** Re-apply the plugin registration below (and
> re-copy the userlib jars, which are *not* removed by extraction but are
> also not added automatically) after every redeploy.

The OTel SDK bootstrap plugin (`fluxnova-engine-plugin-otel`, part of
`fluxnova-bpm-platform`'s `engine-plugins/otel-plugin` module) is **not**
bundled into the distro automatically — copy it and its runtime dependencies
into `userlib` manually:

```powershell
cd C:\dev\fluxnova-bpm-platform\engine-plugins\otel-plugin
mvn dependency:copy-dependencies -DoutputDirectory=target\dependency

Copy-Item target\fluxnova-engine-plugin-otel-3.1.0-SNAPSHOT.jar `
  C:\dev\fluxnova-bpm-platform\local\configuration\userlib\
Copy-Item target\dependency\opentelemetry-*.jar, target\dependency\okhttp-*.jar, `
  target\dependency\okio*.jar, target\dependency\annotations-*.jar `
  C:\dev\fluxnova-bpm-platform\local\configuration\userlib\
```

Then register the plugin in `local\configuration\default.yml` under
`fluxnova.bpm.run`:

```yaml
process-engine-plugins:
  - plugin-class: org.finos.fluxnova.bpm.engine.plugin.otel.OpenTelemetryProcessEnginePlugin
    plugin-parameters:
      enabled: true
      exporterEndpoint: http://localhost:4317
      exporterProtocol: grpc
      serviceName: fluxnova-agentic-subprocess-local
      metricExportIntervalMillis: 5000
```

Confirm on startup (see step 8) that both `AgentHistoryEnginePlugin` and
`OpenTelemetryProcessEnginePlugin` activate cleanly and `RUN-CR001` shows
`OpenTelemetryProcessEnginePlugin` in the activated plugin list.

## 4. Run an OTel Collector locally

Install the [`otelcol-contrib`](https://github.com/open-telemetry/opentelemetry-collector-releases/releases)
distribution (e.g. the Windows zip/MSI). Use a `config.yaml` like:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

exporters:
  debug:
    verbosity: detailed
  prometheus:
    endpoint: 0.0.0.0:8889
  otlphttp/mlflow:
    # Use the signal-specific *_endpoint key, not the generic `endpoint`
    # (which is treated as a base URL and gets `/v1/traces` auto-appended,
    # causing a double-path 404 if the full path is already there).
    traces_endpoint: http://localhost:5000/v1/traces
    headers:
      x-mlflow-experiment-id: "1"

service:
  pipelines:
    metrics:
      receivers: [otlp]
      exporters: [prometheus, debug]
    traces:
      receivers: [otlp]
      exporters: [otlphttp/mlflow, debug]
```

Start it (from the install directory):

```powershell
.\otelcol-contrib.exe --config config.yaml
```

**To restart** after a config change (it logs to the Windows Application
Event Log under source `otelcol-contrib`, not a file):

```powershell
Get-CimInstance Win32_Process -Filter "Name='otelcol-contrib.exe'" |
  Select-Object ProcessId, CreationDate, CommandLine
Stop-Process -Id <ProcessId> -Force
# relaunch as above, from the same directory as config.yaml
```

## 5. Run Prometheus locally

No Docker needed — download the native binary from
[prometheus.io/download](https://prometheus.io/download/). Add scrape jobs
to `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: fluxnova
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["localhost:8080"]
  - job_name: otel-collector
    static_configs:
      - targets: ["localhost:8889"]
```

```powershell
.\prometheus.exe --config.file=prometheus.yml
```

Verify both jobs are `up` at http://localhost:9090/targets.

## 6. Run Grafana locally

Download the Grafana OSS Windows zip from
[grafana.com/grafana/download](https://grafana.com/grafana/download), then:

```powershell
.\bin\grafana-server.exe
```

Open http://localhost:3000 (default login `admin`/`admin`), add a
**Prometheus** data source pointing at `http://localhost:9090`, then import
one of the dashboards in this folder:

- `agentic-subprocess-otel-dashboard.json` (classic schema) — **Dashboards →
  New → Import**, upload the file, select the Prometheus datasource.
- `agentic-subprocess-otel-dashboard.v2.json` (Grafana 12/13 v2 schema) —
  create/open a dashboard → Edit → Settings → JSON Model → paste contents →
  Save.

See `README.md` in this folder for panel details.

## 7. Run MLflow locally (for traces)

```powershell
pip install mlflow
mlflow server --host 0.0.0.0 --port 5000
```

Open http://localhost:5000, create an experiment (e.g. `FluxNova`), and note
its **experiment ID** shown in the UI/URL — set that value as the
`x-mlflow-experiment-id` header in the collector's `otlphttp/mlflow` exporter
config (step 4).

## 8. Run the agentic subprocess

```powershell
cd C:\dev\fluxnova-bpm-platform\local
.\start.bat
```

Check the startup log for:

```
... AgentHistoryEnginePlugin ... Agent history schema initialised ...
... ENGINE-12003 Plugin 'CompositeProcessEnginePlugin[...AgentHistoryEnginePlugin, ..., OpenTelemetryProcessEnginePlugin]' activated ...
```

Then trigger a process instance containing an ad hoc agent subprocess (via
Cockpit, Tasklist, or the REST API at `http://localhost:8080/engine-rest`)
and let it complete.

## 9. View metrics and traces

**Metrics (Grafana):**
- Open the imported dashboard; panels cover call/invocation rates, tool
  error rate, `chat`/`execute_tool`/`invoke_agent` duration percentiles, and
  token usage by provider/model.
- Rate/quantile panels need a few runs and/or a narrow time range (e.g. last
  15m) to render meaningfully — a single run will show `0.00` at 5m rate
  resolution, which is expected, not broken.
- Spot-check directly via Prometheus, e.g.:
  ```
  http://localhost:9090/api/v1/query?query=gen_ai_client_operation_duration_seconds_count
  ```

**Traces (MLflow):**
- Open http://localhost:5000, select your experiment, and view the trace
  list / span tree (`invoke_agent` → `chat` / `execute_tool`), token usage,
  and cost metadata attached to each trace.
- Or via API:
  ```
  http://localhost:5000/api/2.0/mlflow/traces?experiment_ids=<id>
  ```

**Troubleshooting the pipeline**, in order, using the collector's
self-observability metrics scraped into Prometheus:
1. `otelcol_receiver_accepted_metric_points_total` /
   `otelcol_receiver_accepted_spans_total` — did the engine send anything?
2. `otelcol_exporter_sent_spans_total{exporter="otlphttp/mlflow"}` vs
   `otelcol_exporter_send_failed_spans_total{exporter="otlphttp/mlflow"}` —
   did the collector successfully deliver to MLflow?
3. If failing, test the endpoint directly:
   ```powershell
   Invoke-WebRequest -Uri "http://localhost:5000/v1/traces" -Method Post `
     -Body ([byte[]]@()) -ContentType "application/x-protobuf" `
     -Headers @{"x-mlflow-experiment-id"="<id>"} -SkipHttpErrorCheck
   ```
   A `400` with `"Invalid OpenTelemetry format - no spans found"` means the
   endpoint/path/headers are correct. A `404` usually means a doubled
   `/v1/traces/v1/traces` path — use `traces_endpoint` (not `endpoint`) in
   the collector's `otlphttp` exporter config.
