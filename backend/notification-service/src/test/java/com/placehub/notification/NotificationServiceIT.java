package com.placehub.notification;

import com.placehub.common.security.JwtKeys;
import com.placehub.notification.document.Notification;
import com.placehub.notification.messaging.ApplicationEvents;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MongoDB + Kafka: the same event published twice yields one notification per recipient (skipped without Docker). */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "placehub.security.jwt-secret=" + NotificationServiceIT.SECRET
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificationServiceIT {

    static final String SECRET = "integration-test-secret-at-least-32-bytes!";

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

    @Autowired
    MockMvc mvc;
    @Autowired
    MongoTemplate mongoTemplate;

    static String token(UUID sub, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("placehub-user-service").subject(sub.toString())
                .claim("role", role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        return "Bearer " + NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(SECRET)).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    void publish(String topic, String key, String value) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    List<Notification> forEvent(String eventId) {
        return mongoTemplate.find(Query.query(Criteria.where("eventId").is(eventId)), Notification.class);
    }

    void await(String eventId, int expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (forEvent(eventId).size() >= expected) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Expected " + expected + " notifications for event " + eventId);
    }

    @Test
    void duplicateDeliveryIsIdempotentAndReadsAreRecipientScoped() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        UUID recruiter = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"APPLICATION_CREATED","eventVersion":1,"occurredAt":"2026-01-01T10:00:00Z",
                 "applicationId":"%s","jobId":"%s","studentUserId":"%s","studentName":"IT Student",
                 "recruiterUserId":"%s","jobTitle":"IT Job","companyName":"IT Corp"}"""
                .formatted(eventId, applicationId, UUID.randomUUID(), student, recruiter);

        publish(ApplicationEvents.CREATED_TOPIC, applicationId.toString(), payload);
        publish(ApplicationEvents.CREATED_TOPIC, applicationId.toString(), payload);
        await(eventId.toString(), 2);

        // A later, distinct event proves both duplicates were consumed before we assert.
        UUID marker = UUID.randomUUID();
        publish(ApplicationEvents.CREATED_TOPIC, applicationId.toString(),
                payload.replace(eventId.toString(), marker.toString()));
        await(marker.toString(), 2);

        List<Notification> stored = forEvent(eventId.toString());
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(student.toString(), recruiter.toString());

        mvc.perform(get("/api/notifications").header("Authorization", token(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].recipientUserId").value(student.toString()));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", token(student, "STUDENT")))
                .andExpect(jsonPath("$.count").value(2));

        String recruiterNotificationId = stored.stream()
                .filter(n -> n.getRecipientUserId().equals(recruiter.toString())).findFirst().orElseThrow().getId();
        mvc.perform(patch("/api/notifications/" + recruiterNotificationId + "/read")
                .header("Authorization", token(student, "STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/" + recruiterNotificationId + "/read")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(patch("/api/notifications/read-all").header("Authorization", token(student, "STUDENT")))
                .andExpect(jsonPath("$.updated").value(2));
        mvc.perform(get("/api/notifications").header("Authorization", token(UUID.randomUUID(), "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidEventGoesToDeadLetterWithoutBlockingTheTopic() throws Exception {
        UUID applicationId = UUID.randomUUID();
        publish(ApplicationEvents.CREATED_TOPIC, applicationId.toString(), "{\"eventType\":\"APPLICATION_CREATED\"}");
        UUID eventId = UUID.randomUUID();
        publish(ApplicationEvents.CREATED_TOPIC, applicationId.toString(), """
                {"eventId":"%s","eventType":"APPLICATION_CREATED","eventVersion":1,"occurredAt":"2026-01-01T10:00:00Z",
                 "applicationId":"%s","jobId":"%s","studentUserId":"%s","studentName":"S",
                 "recruiterUserId":"%s","jobTitle":"J","companyName":"C"}"""
                .formatted(eventId, applicationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        await(eventId.toString(), 2);
    }
}
