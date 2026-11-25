package org.finos.fluxnova.ai.mcp.process.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.fluxnova.ai.mcp.process.model.ToolDefinition;
import org.finos.fluxnova.ai.mcp.process.model.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles serialization and collection of process variables for MCP responses.
 *
 * Responsible for:
 * - Collecting configured return variables from process-scope
 * - Normalizing variable values to JSON-compatible types (Map, List, String, Number, Boolean)
 * - Converting between variable map types (VariableMap to HashMap)
 *
 * JSON parsing and serialization is delegated to Jackson ObjectMapper.
 * Spin JSON objects are unwrapped via reflection (Spin is an optional runtime dependency).
 */
public class VariableSerializer {
    private static final Logger LOG = LoggerFactory.getLogger(VariableSerializer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Collects configured return variables from pre-captured process-scope variables.
     *
     * Return variables are resolved exclusively from the pre-captured variables map
     * that was obtained at the first async boundary. If a configured variable does
     * not exist, it is included with a null value. Individual variable retrieval or
     * serialization failures do not prevent other variables from being collected.
     *
     * @param definition the tool definition with return variable configuration
     * @param capturedVariablesObj the pre-captured process-scope variables (VariableMap or Map)
     * @return map of variable names to serialized values (or null if missing/failed)
     */
    public Map<String, Object> collectReturnVariables(ToolDefinition definition, Object capturedVariablesObj) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> capturedVariables = convertToMap(capturedVariablesObj);

        LOG.debug("collectReturnVariables: capturedVariables = {}", capturedVariables);
        LOG.debug("collectReturnVariables: returnVariables count = {}", definition.returnVariables().size());

        for (ToolParameter returnVar : definition.returnVariables()) {
            try {
                Object value = capturedVariables.get(returnVar.name());
                Object normalized = normalize(value);
                values.put(returnVar.name(), normalized);
                LOG.debug("MCP - Collected return variable '{}': {}",
                        returnVar.name(), normalized != null ? "value set" : "null");
            } catch (Exception e) {
                LOG.warn("MCP - Failed to collect return variable '{}', setting to null",
                        returnVar.name(), e);
                values.put(returnVar.name(), null);
            }
        }

        return values;
    }

    /**
     * Normalizes a variable value to a JSON-compatible type.
     *
     * <h2>Supported Types</h2>
     * <ul>
     *   <li>null: returned as null</li>
     *   <li>String, Integer, Long, Boolean: returned as-is</li>
     *   <li>Date: returned as timestamp in milliseconds</li>
     *   <li>Map: recursively normalized (nested values are also normalized)</li>
     *   <li>Collection/List: recursively normalized (elements are also normalized)</li>
     *   <li>Spin JSON objects: unwrapped via reflection, parsed with Jackson</li>
     *   <li>Other objects: serialized via Jackson (round-trip to Map/List/primitive)</li>
     * </ul>
     *
     * @param value the variable value to normalize, may be null
     * @return a JSON-compatible object, or null if normalization failed
     */
    Object normalize(Object value) {
        if (value == null) {
            return null;
        }

        // Primitives and String pass through directly
        if (value instanceof String || value instanceof Integer ||
            value instanceof Long || value instanceof Boolean) {
            return value;
        }

        // Date → epoch millis
        if (value instanceof Date date) {
            return date.getTime();
        }

        // Map → recursively normalize values
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return normalized;
        }

        // Collection/List → recursively normalize elements
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object element : collection) {
                normalized.add(normalize(element));
            }
            return normalized;
        }

        // Spin JSON → unwrap to string → parse with Jackson
        if (isSpinJson(value)) {
            return unwrapSpinJson(value);
        }

        // Anything else → Jackson round-trip to get Map/List/primitive
        try {
            String json = MAPPER.writeValueAsString(value);
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            LOG.debug("MCP - Jackson normalization failed for type {}, using toString: {}",
                    value.getClass().getName(), e.getMessage());
            return value.toString();
        }
    }

    /**
     * Parses a JSON string to a structured object (Map or List).
     *
     * Uses Jackson ObjectMapper for parsing. Returns structured objects rather
     * than escaped strings. If parsing fails, returns the original string.
     *
     * @param jsonString the JSON string to parse
     * @return parsed Map/List, or the original string if not valid JSON
     */
    public Object parseJsonString(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return jsonString;
        }

        try {
            String trimmed = jsonString.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return MAPPER.readValue(trimmed, Object.class);
            }
            return jsonString;
        } catch (Exception e) {
            LOG.debug("MCP - Failed to parse JSON string, returning as-is: {}", e.getMessage());
            return jsonString;
        }
    }

    /**
     * Converts VariableMap (or any Map-like object) to a standard Map.
     * Handles both VariableMap from the Fluxnova framework and standard HashMap objects.
     *
     * @param variablesObj the variables object (could be VariableMap or HashMap)
     * @return a Map representing the variables, never null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToMap(Object variablesObj) {
        if (variablesObj == null) {
            LOG.debug("convertToMap: input is null, returning empty map");
            return new HashMap<>();
        }

        if (variablesObj instanceof Map<?, ?> map) {
            LOG.debug("convertToMap: is instanceof Map");
            return new HashMap<>((Map<String, Object>) map);
        }

        LOG.debug("convertToMap: is NOT instanceof Map, trying entrySet()");

        // Reflection fallback for VariableMap-like objects
        try {
            java.lang.reflect.Method entrySetMethod = variablesObj.getClass().getMethod("entrySet");
            @SuppressWarnings("unchecked")
            java.util.Set<Map.Entry<String, Object>> entries =
                (java.util.Set<Map.Entry<String, Object>>) entrySetMethod.invoke(variablesObj);

            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : entries) {
                result.put(entry.getKey(), entry.getValue());
            }
            LOG.debug("convertToMap: created from entrySet = {}", result);
            return result;
        } catch (Exception e) {
            LOG.warn("MCP - Failed to convert variables object to Map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Checks if a value is a Spin JSON object (runtime detection via class name).
     */
    private boolean isSpinJson(Object value) {
        return value.getClass().getName().startsWith("org.finos.fluxnova.spin.json.");
    }

    /**
     * Unwraps a Spin JSON object by extracting its string representation and parsing with Jackson.
     */
    private Object unwrapSpinJson(Object spinJsonValue) {
        try {
            java.lang.reflect.Method toStringMethod = spinJsonValue.getClass().getMethod("toString");
            String jsonString = (String) toStringMethod.invoke(spinJsonValue);
            return parseJsonString(jsonString);
        } catch (Exception e) {
            LOG.warn("MCP - Failed to unwrap Spin JSON: {}", e.getMessage());
            return null;
        }
    }
}
