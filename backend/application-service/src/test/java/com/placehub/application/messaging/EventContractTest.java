package com.placehub.application.messaging;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Producer-side contract test: our records round-trip the shared fixtures in contracts/events exactly. */
class EventContractTest {

    static final Path CONTRACTS = Path.of("..", "..", "contracts", "events");
    final JsonMapper json = JsonMapper.builder().build();

    JsonNode fixture(String name) throws Exception {
        return json.readTree(Files.readString(CONTRACTS.resolve(name)));
    }

    @Test
    void applicationCreatedMatchesContract() throws Exception {
        JsonNode expected = fixture("application-created.v1.json");
        var event = json.treeToValue(expected, ApplicationEvents.ApplicationCreated.class);
        assertThat(event.eventType()).isEqualTo(ApplicationEvents.CREATED);
        assertThat(event.eventVersion()).isEqualTo(ApplicationEvents.VERSION);
        assertThat(json.readTree(json.writeValueAsString(event))).isEqualTo(expected);
    }

    @Test
    void applicationStatusChangedMatchesContract() throws Exception {
        JsonNode expected = fixture("application-status-changed.v1.json");
        var event = json.treeToValue(expected, ApplicationEvents.ApplicationStatusChanged.class);
        assertThat(event.eventType()).isEqualTo(ApplicationEvents.STATUS_CHANGED);
        assertThat(event.newStatus()).isEqualTo("SHORTLISTED");
        assertThat(json.readTree(json.writeValueAsString(event))).isEqualTo(expected);
    }
}
