package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single entry in the {@code _agentToolCallHistory} process variable, mirroring the data the
 * plugin already puts on {@code execute_tool} OTel spans ({@code gen_ai.tool.name}, the span's
 * OTel status, and, when content capture is enabled, {@code gen_ai.tool.call.arguments}), but
 * exposed as a REST-readable process variable so consumers that can't safely read the trace
 * store (e.g. an MLflow {@code predict_fn}) can still reconstruct the tool-call sequence.
 *
 * @param name      the tool's display name, matching {@code gen_ai.tool.name} on the
 *                  {@code execute_tool} span
 * @param status    {@code "OK"} or {@code "ERROR"}, matching the span's OTel status
 * @param arguments the tool-call arguments, present only when content capture is enabled
 *                  ({@code fluxnova.ai.agent.observability.capture-content: true}); {@code null}
 *                  (and omitted from the serialized JSON) otherwise, so consumers can distinguish
 *                  "not captured" from "captured empty"
 */
public record ToolCallHistoryEntry(
        String name,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode arguments) {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
}
