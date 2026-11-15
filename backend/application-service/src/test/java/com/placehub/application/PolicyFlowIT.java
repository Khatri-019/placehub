package com.placehub.application;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.OutboxEvent;
import com.placehub.application.messaging.ApplicationEvents;
import com.placehub.application.policy.PolicyRepositories.ConsentRepository;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.application.repository.OutboxRepository;
import com.placehub.common.security.JwtKeys;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Placement policy end to end against PostgreSQL + Kafka, with User/Job Service stubbed (skipped without Docker). */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "placehub.security.jwt-secret=" + PolicyFlowIT.SECRET,
        "placehub.security.internal-token=it-internal-token"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PolicyFlowIT {

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
    @Autowired
    ConsentRepository consents;

    final UUID recruiterId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();
    UUID studentId;

    static String token(UUID sub, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("placehub-user-service").subject(sub.toString())
                .claim("role", role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        return "Bearer " + NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(SECRET)).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @BeforeEach
    void student() {
        studentId = UUID.randomUUID();
        wiremock.stubFor(get(urlEqualTo("/internal/users/" + studentId + "/academic-profile"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"userId":"%s","fullName":"Policy Student","email":"p@x.com","branch":"CSE",
                         "graduationYear":2027,"cgpa":8.4,"backlogCount":0,"status":"ACTIVE",
                         "currentResumeId":"%s"}""".formatted(studentId, UUID.randomUUID()))));
        wiremock.stubFor(post(urlMatching("/internal/users/.*/resumes/.*/reference"))
                .willReturn(aResponse().withStatus(204)));
    }

    UUID job(String ctc, String type) {
        return job(ctc, type, "MANUAL");
    }

    UUID job(String ctc, String type, String screening) {
        UUID id = UUID.randomUUID();
        wiremock.stubFor(post(urlEqualTo("/internal/jobs/" + id + "/validate-eligibility"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"" + id + "\",\"eligible\":true,\"reasons\":[]}")));
        wiremock.stubFor(get(urlEqualTo("/internal/jobs/" + id + "/summary"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"jobId":"%s","title":"Job %s","companyId":"%s","companyName":"Corp %s",
                         "recruiterOwnerUserId":"%s","status":"PUBLISHED","deadline":"2099-01-01T00:00:00Z",
                         "ctcLpa":%s,"employmentType":"%s","screeningMode":"%s","autoShortlistMinCgpa":8.0,
                         "autoShortlistMaxBacklogs":0}"""
                        .formatted(id, ctc, UUID.randomUUID(), ctc, recruiterId, ctc, type, screening))));
        return id;
    }

    org.springframework.test.web.servlet.ResultActions apply(UUID jobId, String consentJson) throws Exception {
        String body = consentJson == null ? "{\"jobId\":\"" + jobId + "\"}"
                : "{\"jobId\":\"" + jobId + "\",\"consent\":" + consentJson + "}";
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/applications")
                .header("Authorization", token(studentId, "STUDENT"))
                .header("X-Forwarded-For", "203.0.113.9, 10.0.0.5").header("User-Agent", "PolicyFlowIT")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    UUID created(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return UUID.fromString(json.readTree(r.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asString());
    }

    void move(UUID appId, String... statuses) throws Exception {
        for (String s : statuses) {
            mvc.perform(patch("/api/applications/" + appId + "/status").header("Authorization", token(recruiterId, "RECRUITER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void placementBandsConsentLockAndAutoWithdrawal() throws Exception {
        // Unplaced: no CTC restrictions. Get placed at 12 LPA (unplaced -> no band -> no lock).
        UUID first = created(apply(job("12.00", "FULL_TIME"), null));
        move(first, "SHORTLISTED", "SELECTED");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/applications/me/placement-state")
                        .header("Authorization", token(studentId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCtc").value(12.0))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("18.00 LPA or more")));

        // 8 LPA (0.67x) is blocked.
        apply(job("8.00", "FULL_TIME"), null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details.violations[0].code").value("POLICY_BLOCKED"));

        // 20 LPA (1.67x) needs consent: 422 with the rendered text, then 201 with a valid signature.
        UUID consentJob = job("20.00", "FULL_TIME");
        apply(consentJob, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details.violations[0].code").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.details.consent.renderedText").value(org.hamcrest.Matchers.containsString("Job 20.00")));
        apply(consentJob, "{\"accepted\":true,\"signedName\":\"Someone Else\"}").andExpect(status().isUnprocessableContent());
        UUID consentApp = created(apply(consentJob, "{\"accepted\":true,\"signedName\":\" policy student \"}"));
        var consent = consents.findByApplicationId(consentApp).orElseThrow();
        assertThat(consent.getStudentUserId()).isEqualTo(studentId);
        assertThat(consent.getJobId()).isEqualTo(consentJob);
        assertThat(consent.getClientIp()).isEqualTo("10.0.0.5");
        assertThat(consent.getUserAgent()).isEqualTo("PolicyFlowIT");
        assertThat(consent.getBand()).isEqualTo("[1.50, 2.00) REQUIRE_CONSENT lock");

        // 30 LPA (2.5x) is open without consent; internships are exempt.
        UUID openApp = created(apply(job("30.00", "FULL_TIME"), null));
        UUID internApp = created(apply(job("null", "INTERNSHIP"), null));
        assertThat(consents.findByApplicationId(openApp)).isEmpty();

        // Selection in the lock band locks the student and withdraws the other open applications (SYSTEM).
        move(consentApp, "SHORTLISTED", "SELECTED");
        assertThat(applications.findById(openApp).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(applications.findById(internApp).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        List<OutboxEvent> openEvents = outbox.findByAggregateIdOrderByCreatedAt(openApp);
        JsonNode withdrawal = json.readTree(openEvents.getLast().getPayload());
        assertThat(withdrawal.get("newStatus").asString()).isEqualTo("WITHDRAWN");
        assertThat(withdrawal.get("actorType").asString()).isEqualTo("SYSTEM");
        assertThat(withdrawal.get("changedByUserId").isNull()).isTrue();
        List<OutboxEvent> placementEvents = outbox.findByAggregateIdOrderByCreatedAt(studentId);
        assertThat(placementEvents).extracting(OutboxEvent::getEventType)
                .containsOnly(ApplicationEvents.PLACEMENT_RECORDED).hasSize(2);
        assertThat(json.readTree(placementEvents.getLast().getPayload()).get("locked").asBoolean()).isTrue();

        // A locked student can apply to nothing.
        apply(job("60.00", "FULL_TIME"), null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details.violations[0].code").value("STUDENT_LOCKED"));

        // Admin unlocks with a reason (audited); applying works again.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/students/" + studentId + "/unlock")
                        .header("Authorization", token(adminId, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Offer revoked by company\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/policy/audit")
                        .header("Authorization", token(adminId, "ADMIN")))
                .andExpect(jsonPath("$.content[0].action").value("STUDENT_UNLOCKED"))
                .andExpect(jsonPath("$.content[0].reason").value("Offer revoked by company"));
        created(apply(job("60.00", "FULL_TIME"), null));

        // Recruiters and students cannot touch the admin policy surface.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/consents")
                .header("Authorization", token(recruiterId, "RECRUITER"))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/students/" + studentId + "/lock")
                        .header("Authorization", token(studentId, "STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        String csv = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/consents/export.csv")
                        .param("studentUserId", studentId.toString()).header("Authorization", token(adminId, "ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains(consentApp.toString()).contains("\"policy student\"");
    }

    @Test
    void autoShortlistWritesCreatedThenSystemStatusChangedInOrder() throws Exception {
        UUID app = created(apply(job("10.00", "FULL_TIME", "AUTO_SHORTLIST"), null)); // cgpa 8.4 >= 8.0
        assertThat(applications.findById(app).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(applications.findById(app).orElseThrow().isAutoShortlisted()).isTrue();
        List<OutboxEvent> events = outbox.inInsertionOrder(app);
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly(ApplicationEvents.CREATED, ApplicationEvents.STATUS_CHANGED);
        assertThat(json.readTree(events.get(0).getPayload()).get("autoShortlisted").asBoolean()).isTrue();
        JsonNode change = json.readTree(events.get(1).getPayload());
        assertThat(change.get("actorType").asString()).isEqualTo("SYSTEM");
        assertThat(change.get("changedByUserId").isNull()).isTrue();
        assertThat(change.get("newStatus").asString()).isEqualTo("SHORTLISTED");
        for (int i = 0; i < 50 && outbox.inInsertionOrder(app).stream()
                .anyMatch(e -> e.getStatus() != OutboxEvent.Status.SENT); i++) {
            Thread.sleep(200);
        }
        assertThat(outbox.inInsertionOrder(app)).allMatch(e -> e.getStatus() == OutboxEvent.Status.SENT);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/applications/" + app + "/history")
                        .header("Authorization", token(recruiterId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].toStatus").value("SHORTLISTED"))
                .andExpect(jsonPath("$[1].actorType").value("SYSTEM"));
        // Recruiters keep manual control: they can still reject an auto-shortlisted candidate.
        move(app, "REJECTED");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/applications/" + app + "/history")
                        .header("Authorization", token(UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void newPolicyVersionsLeaveExistingApplicationsUntouched() throws Exception {
        UUID app = created(apply(job("15.00", "FULL_TIME"), null));
        var before = applications.findById(app).orElseThrow();
        String active = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/policy")
                        .header("Authorization", token(adminId, "ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int version = json.readTree(active).get("version").asInt();

        mvc.perform(put("/api/admin/policy").header("Authorization", token(adminId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bands":[{"fromMultiplier":0,"toMultiplier":1.5,"action":"BLOCK","lockOnPlacement":false},
                                          {"fromMultiplier":1.6,"toMultiplier":null,"action":"ALLOW","lockOnPlacement":false}],
                                 "appliesToInternships":false,"consentText":"","changeNote":"broken"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Gap between band 1 and band 2")));

        mvc.perform(put("/api/admin/policy").header("Authorization", token(adminId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bands":[{"fromMultiplier":0,"toMultiplier":1.5,"action":"BLOCK","lockOnPlacement":false},
                                          {"fromMultiplier":1.5,"toMultiplier":2.5,"action":"REQUIRE_CONSENT","lockOnPlacement":true},
                                          {"fromMultiplier":2.5,"toMultiplier":null,"action":"ALLOW","lockOnPlacement":false}],
                                 "appliesToInternships":false,"resumeRequired":true,"withdrawOtherApplicationsOnLock":true,
                                 "consentText":"Consent for {{jobTitle}}","changeNote":"Widen the consent band to 2.5x"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/policy/simulate")
                        .header("Authorization", token(adminId, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentCtc\":12,\"jobCtc\":24,\"employmentType\":\"FULL_TIME\",\"locked\":false}"))
                .andExpect(jsonPath("$.decision").value("REQUIRE_CONSENT"))
                .andExpect(jsonPath("$.policyVersion").value(version + 1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/policy/versions")
                        .header("Authorization", token(adminId, "ADMIN")))
                .andExpect(jsonPath("$.content[0].version").value(version + 1))
                .andExpect(jsonPath("$.content[1].active").value(false));

        var after = applications.findById(app).orElseThrow();
        assertThat(after.getPolicyVersionSnapshot()).isEqualTo(before.getPolicyVersionSnapshot());
        assertThat(after.getPolicyDecisionSnapshot()).isEqualTo(before.getPolicyDecisionSnapshot());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());

        // Restore the default bands so other tests see v1 semantics.
        mvc.perform(put("/api/admin/policy").header("Authorization", token(adminId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bands":[{"fromMultiplier":0,"toMultiplier":1.5,"action":"BLOCK","lockOnPlacement":false},
                                  {"fromMultiplier":1.5,"toMultiplier":2.0,"action":"REQUIRE_CONSENT","lockOnPlacement":true},
                                  {"fromMultiplier":2.0,"toMultiplier":null,"action":"ALLOW","lockOnPlacement":false}],
                         "appliesToInternships":false,"consentText":"Consent for {{jobTitle}}","changeNote":"Restore default"}"""))
                .andExpect(status().isOk());
    }
}
