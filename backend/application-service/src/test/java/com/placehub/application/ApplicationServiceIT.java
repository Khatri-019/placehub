package com.placehub.application;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.placehub.application.entity.OutboxEvent;
import com.placehub.application.messaging.ApplicationEvents;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.application.repository.OutboxRepository;
import com.placehub.common.security.JwtKeys;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL + Kafka containers, Feign dependencies stubbed with WireMock, Eureka disabled. Verifies
 * apply -> outbox -> Kafka message and the main error mappings (skipped without Docker).
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "placehub.security.jwt-secret=" + ApplicationServiceIT.SECRET,
        "placehub.security.internal-token=it-internal-token"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationServiceIT {

    static final String SECRET = "integration-test-secret-at-least-32-bytes!";
    static final WireMockServer wiremock = new WireMockServer(options().dynamicPort());

    static {
        wiremock.start();
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

    @DynamicPropertySource
    static void feignUrls(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.user-service.url", wiremock::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.job-service.url", wiremock::baseUrl);
    }

    @AfterAll
    static void stopWiremock() {
        wiremock.stop();
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    ApplicationRepository applications;
    @Autowired
    OutboxRepository outbox;

    UUID studentId;
    UUID jobId;
    UUID resumeId;
    final UUID recruiterId = UUID.randomUUID();

    static String token(UUID sub, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("placehub-user-service").subject(sub.toString())
                .claim("role", role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        return "Bearer " + NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(SECRET)).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @BeforeEach
    void stubs() {
        studentId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        resumeId = UUID.randomUUID();
        wiremock.stubFor(get(urlEqualTo("/internal/users/" + studentId + "/academic-profile"))
                .withHeader("X-Internal-Token", equalTo("it-internal-token"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"userId":"%s","fullName":"IT Student","email":"it@x.com","branch":"CSE",
                         "graduationYear":2027,"cgpa":8.4,"backlogCount":0,"status":"ACTIVE",
                         "currentResumeId":"%s"}""".formatted(studentId, resumeId))));
        wiremock.stubFor(post(urlEqualTo("/internal/users/" + studentId + "/resumes/" + resumeId + "/reference"))
                .withHeader("X-Internal-Token", equalTo("it-internal-token"))
                .willReturn(aResponse().withStatus(204)));
        wiremock.stubFor(get(urlEqualTo("/internal/users/" + studentId + "/resumes/" + resumeId + "/file"))
                .withHeader("X-Internal-Token", equalTo("it-internal-token"))
                .willReturn(aResponse().withHeader("Content-Type", "application/pdf")
                        .withHeader("Content-Disposition", "attachment; filename=\"it-cv.pdf\"")
                        .withBody("%PDF-1.4 it resume")));
        wiremock.stubFor(post(urlEqualTo("/internal/jobs/" + jobId + "/validate-eligibility"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"" + jobId + "\",\"eligible\":true,\"reasons\":[]}")));
        wiremock.stubFor(get(urlEqualTo("/internal/jobs/" + jobId + "/summary"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"jobId":"%s","title":"IT Job","companyId":"%s","companyName":"IT Corp",
                         "recruiterOwnerUserId":"%s","status":"PUBLISHED","deadline":"2099-01-01T00:00:00Z",
                         "ctcLpa":11.5,"employmentType":"FULL_TIME"}"""
                        .formatted(jobId, UUID.randomUUID(), recruiterId))));
    }

    String apply(UUID job, String auth, int expected) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/applications")
                        .header("Authorization", auth).header("X-Correlation-Id", "it-corr-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":\"" + job + "\"}"))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void applyWritesOutboxAndPublishesToKafka() throws Exception {
        String body = apply(jobId, token(studentId, "STUDENT"), 201);
        JsonNode created = json.readTree(body);
        UUID applicationId = UUID.fromString(created.get("id").asString());
        assertThat(created.get("companyName").asString()).isEqualTo("IT Corp");
        assertThat(created.get("ctcLpa").decimalValue()).isEqualByComparingTo("11.50");
        assertThat(created.get("employmentType").asString()).isEqualTo("FULL_TIME");
        wiremock.verify(getRequestedFor(urlPathMatching("/internal/users/.*/academic-profile"))
                .withHeader("X-Correlation-Id", equalTo("it-corr-1")));

        // Committed together: application + outbox row.
        assertThat(applications.findById(applicationId)).isPresent();
        List<OutboxEvent> rows = outbox.findByAggregateIdOrderByCreatedAt(applicationId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getEventType()).isEqualTo(ApplicationEvents.CREATED);

        // Duplicate is rejected by the unique constraint.
        apply(jobId, token(studentId, "STUDENT"), 409);

        ConsumerRecord<String, String> record = pollFor(ApplicationEvents.CREATED_TOPIC, applicationId.toString());
        JsonNode payload = json.readTree(record.value());
        assertThat(payload.get("eventId").asString()).isEqualTo(rows.getFirst().getId().toString());
        assertThat(payload.get("recruiterUserId").asString()).isEqualTo(recruiterId.toString());
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(new String(record.headers().lastHeader("X-Correlation-Id").value())).isEqualTo("it-corr-1");
        awaitStatus(rows.getFirst().getId(), OutboxEvent.Status.SENT);

        // Recruiter shortlists -> status-changed event.
        mvc.perform(patch("/api/applications/" + applicationId + "/status").header("Authorization",
                                token(recruiterId, "RECRUITER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\",\"note\":\"good\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHORTLISTED"));
        mvc.perform(patch("/api/applications/" + applicationId + "/status").header("Authorization",
                                token(UUID.randomUUID(), "RECRUITER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
        ConsumerRecord<String, String> changed = pollFor(ApplicationEvents.STATUS_CHANGED_TOPIC,
                applicationId.toString());
        assertThat(json.readTree(changed.value()).get("newStatus").asString()).isEqualTo("SHORTLISTED");
    }

    @Test
    void resumeSnapshotIsStreamedOnlyToParticipants() throws Exception {
        JsonNode created = json.readTree(apply(jobId, token(studentId, "STUDENT"), 201));
        String appId = created.get("id").asString();
        assertThat(created.get("hasResume").asBoolean()).isTrue();
        wiremock.verify(postRequestedFor(urlEqualTo("/internal/users/" + studentId + "/resumes/" + resumeId + "/reference")));
        assertThat(applications.findById(UUID.fromString(appId)).orElseThrow().getResumeIdSnapshot()).isEqualTo(resumeId);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/applications/" + appId + "/resume")
                        .header("Authorization", token(recruiterId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("%PDF-1.4 it resume"));
        // IDOR: a different recruiter and a different student get 404.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/applications/" + appId + "/resume")
                        .header("Authorization", token(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/applications/" + appId + "/resume")
                        .header("Authorization", token(UUID.randomUUID(), "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingResumeIs422WithResumeRequiredCode() throws Exception {
        UUID noResumeStudent = UUID.randomUUID();
        wiremock.stubFor(get(urlEqualTo("/internal/users/" + noResumeStudent + "/academic-profile"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"userId":"%s","fullName":"No CV","email":"n@x.com","branch":"CSE",
                         "graduationYear":2027,"cgpa":8.4,"backlogCount":0,"status":"ACTIVE"}""".formatted(noResumeStudent))));
        JsonNode error = json.readTree(apply(jobId, token(noResumeStudent, "STUDENT"), 422));
        assertThat(error.get("details").get("violations").get(0).get("code").asString()).isEqualTo("RESUME_REQUIRED");
        assertThat(error.get("message").asString()).isEqualTo("Upload your resume before applying");
    }

    @Test
    void ineligibleIs422WithReasons() throws Exception {
        wiremock.stubFor(post(urlEqualTo("/internal/jobs/" + jobId + "/validate-eligibility"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(
                        "{\"jobId\":\"" + jobId + "\",\"eligible\":false,\"reasons\":[\"Minimum CGPA required is 9.00; yours is 8.40\"]}")));
        String body = apply(jobId, token(studentId, "STUDENT"), 422);
        JsonNode error = json.readTree(body);
        assertThat(error.get("details").get("reasons").get(0).asString()).contains("Minimum CGPA");
        assertThat(error.get("error").asString()).isEqualTo("Unprocessable Entity");
    }

    @Test
    void dependencyFailureIs503AndUnknownJobIs404() throws Exception {
        UUID brokenJob = UUID.randomUUID();
        wiremock.stubFor(post(urlEqualTo("/internal/jobs/" + brokenJob + "/validate-eligibility"))
                .willReturn(aResponse().withStatus(500)));
        String body = apply(brokenJob, token(studentId, "STUDENT"), 503);
        assertThat(json.readTree(body).get("message").asString())
                .isEqualTo("Eligibility could not be verified. Try again shortly.");

        UUID missingJob = UUID.randomUUID();
        wiremock.stubFor(post(urlEqualTo("/internal/jobs/" + missingJob + "/validate-eligibility"))
                .willReturn(aResponse().withStatus(404)));
        apply(missingJob, token(studentId, "STUDENT"), 404);
        assertThat(applications.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void recruitersCannotApply() throws Exception {
        apply(jobId, token(recruiterId, "RECRUITER"), 403);
    }

    ConsumerRecord<String, String> pollFor(String topic, String key) {
        Properties props = new Properties();
        props.putAll(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            List<String> seen = new ArrayList<>();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    seen.add(r.key());
                    if (key.equals(r.key())) {
                        return r;
                    }
                }
            }
            throw new AssertionError("No record with key " + key + " on " + topic + "; saw " + seen);
        }
    }

    void awaitStatus(UUID eventId, OutboxEvent.Status expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (outbox.findById(eventId).map(OutboxEvent::getStatus).orElse(null) == expected) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Outbox event " + eventId + " never reached " + expected);
    }
}
