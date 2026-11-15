package com.placehub.notification.messaging;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validation rules plus the consumer-side contract test against contracts/events/*.json. */
class EventParserTest {

    static final Path CONTRACTS = Path.of("..", "..", "contracts", "events");
    final JsonMapper json = JsonMapper.builder().build();
    final EventParser parser = new EventParser(json);

    String fixture(String name) throws Exception {
        return Files.readString(CONTRACTS.resolve(name));
    }

    @Test
    void createdFixtureDeserializes() throws Exception {
        var e = parser.parseCreated(fixture("application-created.v1.json"));
        assertThat(e.eventVersion()).isEqualTo(1);
        assertThat(e.studentName()).isEqualTo("Demo Student");
        assertThat(e.recruiterUserId().toString()).isEqualTo("00000000-0000-0000-0000-000000000002");
        assertThat(e.companyName()).isEqualTo("Northstar Technologies");
    }

    @Test
    void statusChangedFixtureDeserializes() throws Exception {
        var e = parser.parseStatusChanged(fixture("application-status-changed.v1.json"));
        assertThat(e.oldStatus()).isEqualTo("APPLIED");
        assertThat(e.newStatus()).isEqualTo("SHORTLISTED");
        assertThat(e.changedByUserId()).isNotNull();
    }

    @Test
    void missingEventIdIsInvalid() throws Exception {
        ObjectNode node = (ObjectNode) json.readTree(fixture("application-created.v1.json"));
        node.remove("eventId");
        assertThatThrownBy(() -> parser.parseCreated(node.toString())).isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void unknownVersionIsInvalid() throws Exception {
        ObjectNode node = (ObjectNode) json.readTree(fixture("application-created.v1.json"));
        node.put("eventVersion", 2);
        assertThatThrownBy(() -> parser.parseCreated(node.toString())).isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void wrongTypeAndGarbageAreInvalid() throws Exception {
        assertThatThrownBy(() -> parser.parseStatusChanged(fixture("application-created.v1.json")))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> parser.parseCreated("not json")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> parser.parseCreated("[]")).isInstanceOf(InvalidEventException.class);
    }
}
