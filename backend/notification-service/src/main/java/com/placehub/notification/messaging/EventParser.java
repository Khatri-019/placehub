package com.placehub.notification.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/** Parses and validates raw event JSON. Any problem raises {@link InvalidEventException} (non-retryable). */
@Component
public class EventParser {

    public static final int SUPPORTED_VERSION = 1;
    private static final List<String> COMMON_REQUIRED = List.of("eventId", "eventType", "eventVersion",
            "applicationId", "jobId", "studentUserId", "recruiterUserId", "jobTitle");

    private final JsonMapper jsonMapper;

    public EventParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public ApplicationEvents.ApplicationCreated parseCreated(String json) {
        JsonNode node = validate(json, ApplicationEvents.CREATED, List.of());
        return convert(node, ApplicationEvents.ApplicationCreated.class);
    }

    public ApplicationEvents.ApplicationStatusChanged parseStatusChanged(String json) {
        JsonNode node = validate(json, ApplicationEvents.STATUS_CHANGED, List.of("newStatus"));
        return convert(node, ApplicationEvents.ApplicationStatusChanged.class);
    }

    private JsonNode validate(String json, String expectedType, List<String> extraRequired) {
        JsonNode node;
        try {
            node = json == null ? null : jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event is not valid JSON", e);
        }
        if (node == null || !node.isObject()) {
            throw new InvalidEventException("Event payload must be a JSON object");
        }
        for (String field : COMMON_REQUIRED) {
            requireText(node, field);
        }
        for (String field : extraRequired) {
            requireText(node, field);
        }
        if (!node.get("eventVersion").canConvertToInt() || node.get("eventVersion").asInt() != SUPPORTED_VERSION) {
            throw new InvalidEventException("Unsupported eventVersion " + node.get("eventVersion"));
        }
        if (!expectedType.equals(node.get("eventType").asString())) {
            throw new InvalidEventException("Unexpected eventType " + node.get("eventType").asString());
        }
        try {
            UUID.fromString(node.get("eventId").asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("eventId is not a UUID", e);
        }
        return node;
    }

    private static void requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new InvalidEventException("Missing required field '" + field + "'");
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return jsonMapper.treeToValue(node, type);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event does not match the v1 contract", e);
        }
    }
}
