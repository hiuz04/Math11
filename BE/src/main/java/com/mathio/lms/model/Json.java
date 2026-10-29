package com.mathio.lms.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Json {
    public static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static {
        MAPPER.getFactory()
                .setStreamReadConstraints(
                        com.fasterxml.jackson.core.StreamReadConstraints.builder()
                                .maxNestingDepth(64)
                                .maxStringLength(12_000_000)
                                .build());
        MAPPER.getFactory()
                .configure(
                        com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
                        true);
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static JsonNode parse(String value) {
        try {
            return MAPPER.readTree(value);
        } catch (Exception e) {
            throw new ApiException(400, "JSON không hợp lệ.");
        }
    }

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static String text(JsonNode input, String key, int max) {
        var v = input.path(key);
        String s = v.isTextual() ? v.asText().trim() : "";
        ApiException.require(
                !s.isEmpty() && s.length() <= max, 400, "Trường " + key + " không hợp lệ.");
        return s;
    }

    public static long id(JsonNode input, String key) {
        long id = input.path(key).asLong();
        ApiException.require(id > 0, 400, "ID không hợp lệ.");
        return id;
    }

    public static String password(JsonNode input, String key, int minimum) {
        var v = input.path(key);
        String s = v.isTextual() ? v.asText() : "";
        ApiException.require(
                s.length() >= minimum
                        && s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
                400,
                "Mật khẩu phải có " + minimum + " ký tự trở lên và tối đa 72 bytes UTF-8.");
        return s;
    }

    private Json() {}
}
