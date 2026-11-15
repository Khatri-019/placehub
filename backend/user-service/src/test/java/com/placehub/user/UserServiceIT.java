package com.placehub.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL-backed register / login / me flow through MockMvc (skipped when Docker is unavailable). */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "placehub.security.jwt-secret=integration-test-secret-at-least-32-bytes!",
        "placehub.security.internal-token=it-internal-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class UserServiceIT {

    static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    static final String STUDENT_ID = "00000000-0000-0000-0000-000000000003";
    static final String RECRUITER_ID = "00000000-0000-0000-0000-000000000002";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;

    String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }

    @Test
    void registerLoginAndMe() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@Example.com";
        String reg = """
                {"fullName":"IT Student","email":"%s","password":"Secret123","collegeName":"PIT",
                 "branch":"CSE","graduationYear":2027,"cgpa":8.4,"backlogCount":0}""".formatted(email);
        mvc.perform(post("/api/auth/register/student").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/register/student").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));

        String token = login(email.toUpperCase(), "Secret123");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentDetails.branch").value("CSE"))
                .andExpect(jsonPath("$.studentDetails.cgpa").value(8.4));
    }

    @Test
    void validationErrorsUseFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register/student").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"branch\":\"CSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.email").exists())
                .andExpect(jsonPath("$.details.fieldErrors.password").exists());
        mvc.perform(post("/api/auth/register/student").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branch\":\"Computer Science\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotBeSelfRegistered() throws Exception {
        String reg = """
                {"fullName":"Sneaky","email":"sneaky-%s@x.com","password":"Secret123","companyName":"X",
                 "designation":"Y","role":"ADMIN"}""".formatted(UUID.randomUUID());
        mvc.perform(post("/api/auth/register/recruiter").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    void wrongPasswordIs401AndMeWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@placehub.local\",\"password\":\"Nope12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpointsAndSelfDeactivation() throws Exception {
        String admin = login("admin@placehub.local", "PlaceHub@123");
        String student = login("student@placehub.local", "PlaceHub@123");
        mvc.perform(get("/api/admin/users").param("role", "STUDENT").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").param("sort", "passwordHash,asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/" + ADMIN_ID + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DEACTIVATED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/not-a-uuid/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatedUserCannotLogInAndIsReportedInactiveInternally() throws Exception {
        String admin = login("admin@placehub.local", "PlaceHub@123");
        String email = "deact-" + UUID.randomUUID() + "@x.com";
        String body = mvc.perform(post("/api/auth/register/student").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Temp","email":"%s","password":"Secret123","collegeName":"PIT",
                                 "branch":"ME","graduationYear":2027,"cgpa":7,"backlogCount":1}""".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body);
        String id = created.get("id").asString();
        mvc.perform(patch("/api/admin/users/" + id + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DEACTIVATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/users/" + id + "/academic-profile").header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    static MockMultipartFile pdf(String name, String body) {
        return new MockMultipartFile("file", name, "application/pdf",
                ("%PDF-1.4\n" + body + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
    }

    String freshStudentToken(String tag) throws Exception {
        String email = tag + "-" + UUID.randomUUID() + "@x.com";
        mvc.perform(post("/api/auth/register/student").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Resume Owner","email":"%s","password":"Secret123","collegeName":"PIT",
                 "branch":"CSE","graduationYear":2027,"cgpa":8,"backlogCount":0}""".formatted(email)))
                .andExpect(status().isCreated());
        return login(email, "Secret123");
    }

    String userId(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    @Test
    void resumeUploadDownloadVersionsAndValidation() throws Exception {
        String token = freshStudentToken("resume");
        String bearer = "Bearer " + token;
        mvc.perform(get("/api/users/me/resume").header("Authorization", bearer)).andExpect(status().isNotFound());

        String first = mvc.perform(multipart("/api/users/me/resume").file(pdf("../../etc/My CV.pdf", "v1"))
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("My CV.pdf"))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.sha256").isString())
                .andReturn().getResponse().getContentAsString();
        String firstId = json.readTree(first).get("id").asString();

        mvc.perform(get("/api/users/me/resume/file").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));

        // Non-PDF content with a .pdf name and PDF content type is rejected by the magic-byte check.
        mvc.perform(multipart("/api/users/me/resume").file(new MockMultipartFile("file", "evil.pdf", "application/pdf",
                        "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer))
                .andExpect(status().isUnsupportedMediaType());
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);
        mvc.perform(multipart("/api/users/me/resume").file(new MockMultipartFile("file", "big.pdf", "application/pdf", big))
                        .header("Authorization", bearer))
                .andExpect(status().isPayloadTooLarge());

        // Three more uploads: only the newest 3 unreferenced versions are kept, plus referenced ones.
        String studentId = userId(token);
        mvc.perform(post("/internal/users/" + studentId + "/resumes/" + firstId + "/reference")
                .header("X-Internal-Token", "it-internal-token")).andExpect(status().isNoContent());
        for (int i = 2; i <= 5; i++) {
            mvc.perform(multipart("/api/users/me/resume").file(pdf("cv" + i + ".pdf", "v" + i))
                    .header("Authorization", bearer)).andExpect(status().isCreated());
        }
        String overview = mvc.perform(get("/api/users/me/resume").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.filename").value("cv5.pdf"))
                .andReturn().getResponse().getContentAsString();
        JsonNode versions = json.readTree(overview).get("versions");
        org.assertj.core.api.Assertions.assertThat(versions.size()).isEqualTo(4); // cv5, cv4, cv3 + referenced v1
        org.assertj.core.api.Assertions.assertThat(versions.toString()).contains(firstId).doesNotContain("cv2.pdf");

        // Referenced versions cannot be deleted; unreferenced ones can.
        mvc.perform(delete("/api/users/me/resume/" + firstId).header("Authorization", bearer))
                .andExpect(status().isConflict());

        // The internal stream is owner-scoped: the right resume id with another user id is 404 (no IDOR).
        mvc.perform(get("/internal/users/" + studentId + "/resumes/" + firstId + "/file")
                        .header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"));
        mvc.perform(get("/internal/users/" + UUID.randomUUID() + "/resumes/" + firstId + "/file")
                        .header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/users/" + studentId + "/resumes/" + firstId + "/file"))
                .andExpect(status().isUnauthorized());

        // Another student cannot read or delete this student's version through the public API.
        String other = "Bearer " + freshStudentToken("other");
        mvc.perform(get("/api/users/me/resume/" + firstId + "/file").header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/me/resume/" + firstId).header("Authorization", other))
                .andExpect(status().isNotFound());

        // The academic profile exposes the current resume id to Application Service.
        mvc.perform(get("/internal/users/" + studentId + "/academic-profile").header("X-Internal-Token", "it-internal-token"))
                .andExpect(jsonPath("$.currentResumeId").isString());
    }

    @Test
    void recruitersCannotUploadResumes() throws Exception {
        String recruiter = login("recruiter@placehub.local", "PlaceHub@123");
        mvc.perform(multipart("/api/users/me/resume").file(pdf("cv.pdf", "x")).header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEndpointsRequireTheInternalToken() throws Exception {
        String student = login("student@placehub.local", "PlaceHub@123");
        mvc.perform(get("/internal/users/" + STUDENT_ID + "/academic-profile"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/" + STUDENT_ID + "/academic-profile")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/" + STUDENT_ID + "/academic-profile").header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/" + STUDENT_ID + "/academic-profile")
                        .header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("CSE"))
                .andExpect(jsonPath("$.cgpa").value(8.6))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/internal/users/" + RECRUITER_ID + "/academic-profile")
                        .header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/users/" + RECRUITER_ID).header("X-Internal-Token", "it-internal-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("RECRUITER"));
    }
}
