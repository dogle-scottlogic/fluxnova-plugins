package org.finos.fluxnova.ai.mcp.process.engine;

import org.finos.fluxnova.ai.mcp.process.model.ToolDefinition;
import org.finos.fluxnova.ai.mcp.process.model.ToolParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VariableSerializerTest {

    private VariableSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new VariableSerializer();
    }

    // ===================== Tests for normalize method =====================

    @Nested
    @DisplayName("normalize")
    class NormalizeTests {

        @Test
        @DisplayName("null value returns null")
        void nullValue() {
            assertNull(serializer.normalize(null));
        }

        @Test
        @DisplayName("String is returned as-is")
        void stringValue() {
            assertEquals("test string", serializer.normalize("test string"));
        }

        @Test
        @DisplayName("Integer is returned as-is")
        void integerValue() {
            assertEquals(42, serializer.normalize(42));
        }

        @Test
        @DisplayName("Long is returned as-is")
        void longValue() {
            assertEquals(999L, serializer.normalize(999L));
        }

        @Test
        @DisplayName("Boolean is returned as-is")
        void booleanValue() {
            assertEquals(true, serializer.normalize(true));
            assertEquals(false, serializer.normalize(false));
        }

        @Test
        @DisplayName("Date is returned as epoch millis")
        void dateValue() {
            Date date = new Date(1000000000000L);
            assertEquals(1000000000000L, serializer.normalize(date));
        }

        @Test
        @DisplayName("Map is returned as-is (JSON-compatible)")
        void mapValue() {
            Map<String, Object> value = Map.of("id", "123", "name", "John");
            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            assertEquals("123", ((Map<?, ?>) result).get("id"));
            assertEquals("John", ((Map<?, ?>) result).get("name"));
        }

        @Test
        @DisplayName("Nested Map structures are preserved")
        void nestedMapValue() {
            Map<String, Object> nested = Map.of("level", 2, "data", "nested");
            Map<String, Object> value = Map.of(
                "id", "123",
                "nested", nested,
                "list", List.of(1, 2, 3)
            );
            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertEquals("123", resultMap.get("id"));
            assertNotNull(resultMap.get("nested"));
        }

        @Test
        @DisplayName("Map with nested Date values normalizes recursively")
        void mapWithNestedDateValues() {
            Date date = new Date(1700000000000L);
            Map<String, Object> value = new HashMap<>();
            value.put("createdAt", date);
            value.put("name", "test");

            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertEquals(1700000000000L, resultMap.get("createdAt"));
            assertEquals("test", resultMap.get("name"));
        }

        @Test
        @DisplayName("Map with deeply nested non-primitive values normalizes recursively")
        void mapWithDeeplyNestedNonPrimitives() {
            Date innerDate = new Date(1600000000000L);
            Map<String, Object> innerMap = new HashMap<>();
            innerMap.put("timestamp", innerDate);
            innerMap.put("count", 42);

            Map<String, Object> outerMap = new HashMap<>();
            outerMap.put("nested", innerMap);
            outerMap.put("label", "outer");

            Object result = serializer.normalize(outerMap);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<?, ?> resultOuter = (Map<?, ?>) result;
            assertEquals("outer", resultOuter.get("label"));

            assertInstanceOf(Map.class, resultOuter.get("nested"));
            Map<?, ?> resultInner = (Map<?, ?>) resultOuter.get("nested");
            assertEquals(1600000000000L, resultInner.get("timestamp"));
            assertEquals(42, resultInner.get("count"));
        }

        @Test
        @DisplayName("Map with List containing non-primitives normalizes recursively")
        void mapWithListContainingNonPrimitives() {
            Date date1 = new Date(1500000000000L);
            Date date2 = new Date(1600000000000L);
            Map<String, Object> value = new HashMap<>();
            value.put("dates", List.of(date1, date2));
            value.put("name", "schedule");

            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertEquals("schedule", resultMap.get("name"));

            assertInstanceOf(List.class, resultMap.get("dates"));
            List<?> dates = (List<?>) resultMap.get("dates");
            assertEquals(2, dates.size());
            assertEquals(1500000000000L, dates.get(0));
            assertEquals(1600000000000L, dates.get(1));
        }

        @Test
        @DisplayName("Empty Map is preserved after recursive normalization")
        void emptyMapValue() {
            Map<String, Object> value = new HashMap<>();
            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        @DisplayName("Map with null values preserves nulls")
        void mapWithNullValues() {
            Map<String, Object> value = new HashMap<>();
            value.put("key1", "value1");
            value.put("key2", null);
            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertEquals("value1", resultMap.get("key1"));
            assertTrue(resultMap.containsKey("key2"));
            assertNull(resultMap.get("key2"));
        }

        @Test
        @DisplayName("Double is serialized via Jackson round-trip")
        void doubleValue() {
            Object result = serializer.normalize(3.14159);
            assertEquals(3.14159, result);
        }

        @Test
        @DisplayName("Float is serialized via Jackson round-trip")
        void floatValue() {
            Object result = serializer.normalize(2.71f);
            // Jackson converts Float to Double
            assertNotNull(result);
        }

        @Test
        @DisplayName("List is serialized via Jackson round-trip")
        void listValue() {
            List<Object> value = List.of(1, 2, 3, "four");
            Object result = serializer.normalize(value);

            assertNotNull(result);
            assertInstanceOf(List.class, result);
            List<?> list = (List<?>) result;
            assertEquals(4, list.size());
            assertEquals("four", list.get(3));
        }

        @Test
        @DisplayName("Custom object falls back to toString")
        void customObjectFallback() {
            Object value = new CustomObject("test-value");
            Object result = serializer.normalize(value);

            assertEquals("CustomObject(test-value)", result);
        }

        @Test
        @DisplayName("Serializable POJO is converted via Jackson")
        void serializablePojo() {
            SerializablePojo pojo = new SerializablePojo("hello", 42);
            Object result = serializer.normalize(pojo);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals("hello", map.get("name"));
            assertEquals(42, map.get("count"));
        }
    }

    // ===================== Tests for convertToMap =====================

    @Nested
    @DisplayName("convertToMap")
    class ConvertToMapTests {

        @Test
        @DisplayName("null input returns empty map")
        void nullInput() {
            Map<String, Object> result = serializer.convertToMap(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("HashMap input is converted")
        void hashMapInput() {
            Map<String, Object> input = new HashMap<>();
            input.put("key1", "value1");
            input.put("key2", "value2");

            Map<String, Object> result = serializer.convertToMap(input);
            assertNotNull(result);
            assertEquals("value1", result.get("key1"));
            assertEquals("value2", result.get("key2"));
        }

        @Test
        @DisplayName("Immutable Map is converted to new mutable HashMap")
        void immutableMapInput() {
            Map<String, Object> input = Map.of("key", "value");
            Map<String, Object> result = serializer.convertToMap(input);

            assertNotNull(result);
            assertNotSame(input, result);
            assertEquals("value", result.get("key"));
        }

        @Test
        @DisplayName("Empty Map is preserved")
        void emptyMapInput() {
            Map<String, Object> input = new HashMap<>();
            Map<String, Object> result = serializer.convertToMap(input);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ===================== Tests for collectReturnVariables =====================

    @Nested
    @DisplayName("collectReturnVariables")
    class CollectReturnVariablesTests {

        @Test
        @DisplayName("no return variables configured yields empty map")
        void noReturnVariables() {
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(), false);
            Map<String, Object> captured = Map.of("var1", "value1");

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single return variable")
        void singleReturnVariable() {
            ToolParameter returnVar = new ToolParameter("result", "string");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(returnVar), false);
            Map<String, Object> captured = Map.of("result", "test-value");

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertEquals("test-value", result.get("result"));
        }

        @Test
        @DisplayName("multiple return variables")
        void multipleReturnVariables() {
            ToolParameter var1 = new ToolParameter("output1", "string");
            ToolParameter var2 = new ToolParameter("output2", "integer");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var1, var2), false);

            Map<String, Object> captured = new HashMap<>();
            captured.put("output1", "first");
            captured.put("output2", 42);

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertEquals("first", result.get("output1"));
            assertEquals(42, result.get("output2"));
        }

        @Test
        @DisplayName("missing variable is included as null")
        void missingVariable() {
            ToolParameter returnVar = new ToolParameter("missing", "string");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(returnVar), false);

            Map<String, Object> result = serializer.collectReturnVariables(definition, new HashMap<>());
            assertTrue(result.containsKey("missing"));
            assertNull(result.get("missing"));
        }

        @Test
        @DisplayName("mixed existing and missing variables")
        void mixedVariables() {
            ToolParameter var1 = new ToolParameter("exists", "string");
            ToolParameter var2 = new ToolParameter("missing", "string");
            ToolParameter var3 = new ToolParameter("alsoExists", "integer");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var1, var2, var3), false);

            Map<String, Object> captured = new HashMap<>();
            captured.put("exists", "value1");
            captured.put("alsoExists", 999);

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertEquals("value1", result.get("exists"));
            assertNull(result.get("missing"));
            assertEquals(999, result.get("alsoExists"));
        }

        @Test
        @DisplayName("preserves insertion order (LinkedHashMap)")
        void preservesOrder() {
            ToolParameter var1 = new ToolParameter("first", "string");
            ToolParameter var2 = new ToolParameter("second", "string");
            ToolParameter var3 = new ToolParameter("third", "string");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var1, var2, var3), false);

            Map<String, Object> captured = new HashMap<>();
            captured.put("first", "1st");
            captured.put("second", "2nd");
            captured.put("third", "3rd");

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            Object[] keys = result.keySet().toArray();
            assertEquals("first", keys[0]);
            assertEquals("second", keys[1]);
            assertEquals("third", keys[2]);
        }

        @Test
        @DisplayName("handles complex nested objects")
        void complexObjects() {
            Map<String, Object> complexObj = new HashMap<>();
            complexObj.put("nested", Map.of("key", "value"));
            complexObj.put("number", 42);

            ToolParameter var = new ToolParameter("complex", "object");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var), false);

            Map<String, Object> captured = Map.of("complex", complexObj);

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertNotNull(result.get("complex"));
            assertInstanceOf(Map.class, result.get("complex"));
        }

        @Test
        @DisplayName("handles null values in captured variables")
        void nullCapturedValues() {
            ToolParameter var1 = new ToolParameter("nullVar", "string");
            ToolParameter var2 = new ToolParameter("realVar", "string");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var1, var2), false);

            Map<String, Object> captured = new HashMap<>();
            captured.put("nullVar", null);
            captured.put("realVar", "value");

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertTrue(result.containsKey("nullVar"));
            assertNull(result.get("nullVar"));
            assertEquals("value", result.get("realVar"));
        }

        @Test
        @DisplayName("large collection of return variables")
        void largeCollection() {
            List<ToolParameter> vars = new ArrayList<>();
            Map<String, Object> capturedVars = new HashMap<>();

            for (int i = 0; i < 50; i++) {
                String name = "var" + i;
                vars.add(new ToolParameter(name, "string"));
                capturedVars.put(name, "value" + i);
            }

            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), vars, false);

            Map<String, Object> result = serializer.collectReturnVariables(definition, capturedVars);
            assertEquals(50, result.size());
            assertEquals("value0", result.get("var0"));
            assertEquals("value25", result.get("var25"));
            assertEquals("value49", result.get("var49"));
        }

        @Test
        @DisplayName("case-sensitive variable matching")
        void caseSensitive() {
            ToolParameter var1 = new ToolParameter("MyVar", "string");
            ToolParameter var2 = new ToolParameter("myvar", "string");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var1, var2), false);

            Map<String, Object> captured = new HashMap<>();
            captured.put("MyVar", "uppercase");
            captured.put("myvar", "lowercase");

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertEquals("uppercase", result.get("MyVar"));
            assertEquals("lowercase", result.get("myvar"));
        }

        @Test
        @DisplayName("POJO return variable is serialized to Map via Jackson")
        void pojoReturnVariable() {
            SerializablePojo pojo = new SerializablePojo("order-data", 7);

            ToolParameter var = new ToolParameter("output", "object");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var), false);

            Map<String, Object> captured = Map.of("output", pojo);

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertNotNull(result.get("output"));
            assertInstanceOf(Map.class, result.get("output"));
            Map<?, ?> outputMap = (Map<?, ?>) result.get("output");
            assertEquals("order-data", outputMap.get("name"));
            assertEquals(7, outputMap.get("count"));
        }

        @Test
        @DisplayName("Custom object without getters falls back to toString")
        void customObjectReturnVariable() {
            CustomObject custom = new CustomObject("workflow-result");

            ToolParameter var = new ToolParameter("result", "object");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc", List.of(), List.of(var), false);

            Map<String, Object> captured = Map.of("result", custom);

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);
            assertEquals("CustomObject(workflow-result)", result.get("result"));
        }

        @Test
        @DisplayName("Mixed types: primitives, POJO, List, and custom object together")
        void mixedTypeReturnVariables() {
            SerializablePojo pojo = new SerializablePojo("item", 3);
            CustomObject custom = new CustomObject("fallback");

            ToolParameter var1 = new ToolParameter("name", "string");
            ToolParameter var2 = new ToolParameter("count", "integer");
            ToolParameter var3 = new ToolParameter("active", "boolean");
            ToolParameter var4 = new ToolParameter("pojo", "object");
            ToolParameter var5 = new ToolParameter("custom", "object");
            ToolParameter var6 = new ToolParameter("tags", "object");
            ToolDefinition definition = new ToolDefinition("process", "tool", "desc",
                    List.of(), List.of(var1, var2, var3, var4, var5, var6), false);

            Map<String, Object> captured = new HashMap<>();
            captured.put("name", "test");
            captured.put("count", 42);
            captured.put("active", true);
            captured.put("pojo", pojo);
            captured.put("custom", custom);
            captured.put("tags", List.of("alpha", "beta"));

            Map<String, Object> result = serializer.collectReturnVariables(definition, captured);

            assertEquals("test", result.get("name"));
            assertEquals(42, result.get("count"));
            assertEquals(true, result.get("active"));

            assertInstanceOf(Map.class, result.get("pojo"));
            Map<?, ?> pojoMap = (Map<?, ?>) result.get("pojo");
            assertEquals("item", pojoMap.get("name"));
            assertEquals(3, pojoMap.get("count"));

            assertEquals("CustomObject(fallback)", result.get("custom"));

            assertInstanceOf(List.class, result.get("tags"));
            List<?> tags = (List<?>) result.get("tags");
            assertEquals(2, tags.size());
            assertEquals("alpha", tags.get(0));
        }
    }

    // ===================== Tests for parseJsonString =====================

    @Nested
    @DisplayName("parseJsonString")
    class ParseJsonStringTests {

        @Test
        @DisplayName("null input returns null")
        void nullInput() {
            assertNull(serializer.parseJsonString(null));
        }

        @Test
        @DisplayName("empty string returns empty string")
        void emptyInput() {
            assertEquals("", serializer.parseJsonString(""));
        }

        @Test
        @DisplayName("non-JSON string returns as-is")
        void nonJsonInput() {
            assertEquals("not json", serializer.parseJsonString("not json"));
        }

        @Test
        @DisplayName("JSON object is parsed to Map")
        void jsonObject() {
            String json = "{\"id\":\"123\",\"name\":\"John\"}";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals("123", map.get("id"));
            assertEquals("John", map.get("name"));
        }

        @Test
        @DisplayName("nested JSON object")
        void nestedJsonObject() {
            String json = "{\"user\":{\"id\":\"123\",\"profile\":{\"name\":\"John\"}}}";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertInstanceOf(Map.class, map.get("user"));
            Map<?, ?> userMap = (Map<?, ?>) map.get("user");
            assertInstanceOf(Map.class, userMap.get("profile"));
        }

        @Test
        @DisplayName("JSON array is parsed to List")
        void jsonArray() {
            String json = "[1,2,3]";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(List.class, result);
            List<?> list = (List<?>) result;
            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("empty JSON object returns empty Map")
        void emptyJsonObject() {
            Object result = serializer.parseJsonString("{}");
            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        @DisplayName("empty JSON array returns empty List")
        void emptyJsonArray() {
            Object result = serializer.parseJsonString("[]");
            assertInstanceOf(List.class, result);
            assertTrue(((List<?>) result).isEmpty());
        }

        @Test
        @DisplayName("escaped backslash in string value")
        void escapedBackslash() {
            String json = "{\"path\":\"C:\\\\\"}";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals("C:\\", map.get("path"));
        }

        @Test
        @DisplayName("mixed escape sequences")
        void mixedEscapes() {
            String json = "{\"file\":\"C:\\\\Users\\\\test\\\\\",\"message\":\"Line1\\nLine2\",\"quoted\":\"He said \\\"Hi\\\"\"}";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals(3, map.size());

            String file = (String) map.get("file");
            String message = (String) map.get("message");
            String quoted = (String) map.get("quoted");

            assertTrue(file.contains("Users"));
            assertTrue(message.contains("Line1") && message.contains("Line2"));
            assertTrue(quoted.contains("Hi"));
        }

        @Test
        @DisplayName("JSON with various value types")
        void variousTypes() {
            String json = "{\"str\":\"hello\",\"num\":42,\"bool\":true,\"nil\":null,\"arr\":[1,2]}";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals("hello", map.get("str"));
            assertEquals(42, map.get("num"));
            assertEquals(true, map.get("bool"));
            assertNull(map.get("nil"));
            assertInstanceOf(List.class, map.get("arr"));
        }

        @Test
        @DisplayName("deeply nested JSON")
        void deeplyNested() {
            String json = "{\"a\":{\"b\":{\"c\":{\"d\":\"deep\"}}}}";
            Object result = serializer.parseJsonString(json);

            assertInstanceOf(Map.class, result);
            Map<?, ?> a = (Map<?, ?>) ((Map<?, ?>) result).get("a");
            Map<?, ?> b = (Map<?, ?>) a.get("b");
            Map<?, ?> c = (Map<?, ?>) b.get("c");
            assertEquals("deep", c.get("d"));
        }

        @Test
        @DisplayName("invalid JSON returns string as-is")
        void invalidJson() {
            String json = "{invalid json}";
            Object result = serializer.parseJsonString(json);
            assertEquals(json, result);
        }
    }

    // ===================== Helper classes =====================

    static class CustomObject {
        private final String value;

        CustomObject(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "CustomObject(" + value + ")";
        }
    }

    /**
     * A simple POJO that Jackson can serialize (has public getters).
     */
    static class SerializablePojo {
        private final String name;
        private final int count;

        SerializablePojo(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() { return name; }
        public int getCount() { return count; }
    }
}
