package com.placehub.job;

import com.placehub.common.security.JwtKeys;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL-backed company, job lifecycle, browse and eligibility flow (skipped without Docker). */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "placehub.security.jwt-secret=" + JobServiceIT.SECRET,
        "placehub.security.internal-token=it-internal-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class JobServiceIT {

    static final String SECRET = "integration-test-secret-at-least-32-bytes!";
    static final String SEEDED_RECRUITER = "00000000-0000-0000-0000-000000000002";
    static final String JOB1 = "20000000-0000-0000-0000-000000000001";
    static final String DRAFT_JOB = "20000000-0000-0000-0000-000000000004";

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;

    static String token(String sub, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("placehub-user-service").subject(sub)
                .claim("role", role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        return "Bearer " + NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(SECRET)).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    String jobBody(String title) {
        return """
                {"title":"%s","description":"Write code","location":"Pune","employmentType":"FULL_TIME",
                 "ctcLpa":14.5,"minCgpa":7.0,"maxBacklogs":0,"eligibleBranches":["CSE","ISE"],"eligibleGraduationYears":[2027],
                 "deadline":"%s"}""".formatted(title, Instant.now().plus(10, ChronoUnit.DAYS));
    }

    @Test
    void recruiterCompanyJobLifecycleAndStudentBrowse() throws Exception {
        String recruiterId = UUID.randomUUID().toString();
        String recruiter = token(recruiterId, "RECRUITER");
        String student = token(UUID.randomUUID().toString(), "STUDENT");
        String title = "IT Engineer " + UUID.randomUUID();

        mvc.perform(post("/api/jobs").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content(jobBody(title))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Create your company profile first"));

        mvc.perform(post("/api/companies").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Corp\",\"website\":\"https://it.example\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recruiterOwnerUserId").value(recruiterId));
        mvc.perform(post("/api/companies").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Second\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/companies").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\"}")).andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/jobs").header("Authorization", recruiter)
                        .contentType(MediaType.APPLICATION_JSON).content(jobBody(title)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.companyName").value("IT Corp"))
                .andReturn().getResponse().getContentAsString();
        String jobId = json.readTree(created).get("id").asString();

        mvc.perform(get("/api/jobs/" + jobId).header("Authorization", student)).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs").param("q", title).header("Authorization", student))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", recruiter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/jobs").param("q", title).param("branch", "CSE").header("Authorization", student))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eligibleBranches[0]").value("CSE"));
        mvc.perform(get("/api/jobs").param("q", title).param("branch", "ME").header("Authorization", student))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/jobs").param("q", "it corp").header("Authorization", student))
                .andExpect(jsonPath("$.totalElements").value(1));

        String other = token(SEEDED_RECRUITER, "RECRUITER");
        mvc.perform(put("/api/jobs/" + jobId).header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content(jobBody("hijack"))).andExpect(status().isForbidden());

        mvc.perform(get("/api/recruiter/jobs").param("status", "PUBLISHED").header("Authorization", recruiter))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(patch("/api/jobs/" + jobId + "/close").header("Authorization", recruiter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(put("/api/jobs/" + jobId).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content(jobBody("again"))).andExpect(status().isConflict());
    }

    @Test
    void seedDataAndBrowseRules() throws Exception {
        String student = token("00000000-0000-0000-0000-000000000003", "STUDENT");
        mvc.perform(get("/api/jobs").param("size", "50").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(JOB1)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(DRAFT_JOB))))
                .andExpect(jsonPath("$.content[*].status", not(hasItem("DRAFT"))));
        mvc.perform(get("/api/jobs/" + DRAFT_JOB).header("Authorization", student)).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/" + DRAFT_JOB).header("Authorization", token(SEEDED_RECRUITER, "RECRUITER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/jobs").param("sort", "salary,desc").header("Authorization", student))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/jobs/not-a-uuid").header("Authorization", student)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/jobs").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/jobs").param("status", "DRAFT")
                        .header("Authorization", token(UUID.randomUUID().toString(), "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem(DRAFT_JOB)));
        mvc.perform(get("/api/admin/companies").header("Authorization", token(UUID.randomUUID().toString(), "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void internalEligibilityAndSummary() throws Exception {
        mvc.perform(post("/internal/jobs/" + JOB1 + "/validate-eligibility").contentType(MediaType.APPLICATION_JSON)
                .content("{\"branch\":\"CSE\",\"graduationYear\":2027,\"cgpa\":8.6,\"backlogCount\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/jobs/" + JOB1 + "/validate-eligibility").header("X-Internal-Token", "it-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branch\":\"CSE\",\"graduationYear\":2027,\"cgpa\":8.6,\"backlogCount\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true));
        mvc.perform(post("/internal/jobs/" + JOB1 + "/validate-eligibility").header("X-Internal-Token", "it-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branch\":\"ME\",\"graduationYear\":2027,\"cgpa\":6.4,\"backlogCount\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons.length()").value(3));
        mvc.perform(get("/internal/jobs/" + JOB1 + "/summary").header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Northstar Technologies"))
                .andExpect(jsonPath("$.recruiterOwnerUserId").value(SEEDED_RECRUITER))
                .andExpect(jsonPath("$.ctcLpa").value(12.0))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"));
        mvc.perform(get("/internal/jobs/20000000-0000-0000-0000-000000000006/summary")
                        .header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Meridian Analytics"))
                .andExpect(jsonPath("$.ctcLpa").value(30.0))
                .andExpect(jsonPath("$.recruiterOwnerUserId").value("00000000-0000-0000-0000-000000000005"));
        mvc.perform(get("/internal/jobs/20000000-0000-0000-0000-000000000002/summary")
                        .header("X-Internal-Token", "it-internal-token"))
                .andExpect(jsonPath("$.employmentType").value("INTERNSHIP"))
                .andExpect(jsonPath("$.ctcLpa").doesNotExist());
        mvc.perform(get("/internal/jobs/" + UUID.randomUUID() + "/summary").header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isNotFound());
    }
}
