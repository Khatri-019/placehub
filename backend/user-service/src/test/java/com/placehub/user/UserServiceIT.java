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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
