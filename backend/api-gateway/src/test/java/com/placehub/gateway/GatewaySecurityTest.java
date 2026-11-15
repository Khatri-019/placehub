package com.placehub.gateway;

import com.placehub.gateway.filter.AccountStatusLookup;
import com.placehub.gateway.filter.EdgeFilter;
import com.placehub.gateway.filter.EdgeFilterAccess;
import com.placehub.gateway.security.JwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "placehub.gateway.jwt-secret=" + GatewaySecurityTest.SECRET,
        "placehub.gateway.frontend-origin=http://localhost:5173"
})
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    static final String SECRET = "test-secret-for-gateway-at-least-32-bytes!!";
    static final String DEACTIVATED_USER = "00000000-0000-0000-0000-00000000dead";

    /** Stands in for the user-service lookup: one known deactivated account, everyone else unknown (fail open). */
    @TestConfiguration
    static class StatusLookupConfig {
        @Bean
        @Primary
        AccountStatusLookup testAccountStatusLookup() {
            return userId -> DEACTIVATED_USER.equals(userId) ? Mono.just("DEACTIVATED") : Mono.empty();
        }
    }

    @Autowired
    WebTestClient client;

    @Test
    void internalPathsAreRejectedWith404EvenWithToken() {
        client.get().uri("/internal/users/" + UUID.randomUUID())
                .header("X-Internal-Token", "anything")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
        client.get().uri("/internal/jobs/x/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN", Instant.now().plusSeconds(600)))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void publicAuthRoutesDoNotRequireToken() {
        // No service instances are registered in this test, so the load balancer answers 503: but not 401.
        client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotEqualTo(401).isNotEqualTo(403));
    }

    @Test
    void protectedRoutesReturn401WithoutToken() {
        for (String path : new String[]{"/api/users/me", "/api/jobs", "/api/applications/me",
                "/api/notifications", "/api/admin/users"}) {
            client.get().uri(path).exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().exists(EdgeFilter.CORRELATION_HEADER)
                    .expectBody().jsonPath("$.status").isEqualTo(401);
        }
    }

    @Test
    void invalidOrExpiredTokensReturn401() {
        client.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("STUDENT", Instant.now().minusSeconds(3600)))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validTokenPassesGatewaySecurity() {
        client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("STUDENT", Instant.now().plusSeconds(600)))
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotEqualTo(401));
    }

    @Test
    void tokensOfDeactivatedAccountsAreRejectedBeforeRouting() {
        // Security review F1: deactivation must cut off existing tokens, not only new logins.
        String bearer = "Bearer " + token("RECRUITER", Instant.now().plusSeconds(600), DEACTIVATED_USER);
        for (String path : new String[]{"/api/users/me", "/api/jobs", "/api/applications/job/x"}) {
            client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer).exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.message").isEqualTo("Your account is deactivated");
        }
        client.post().uri("/api/companies").header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{\"name\":\"Ghost Co\"}")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void corsPreflightAllowsFrontendOriginOnly() {
        client.options().uri("/api/jobs")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
        client.options().uri("/api/jobs")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void edgeFilterStripsSpoofableHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "00000000-0000-0000-0000-000000000001");
        headers.add("x-user-role", "ADMIN");
        headers.add("X-Internal-Token", "stolen");
        headers.add("Authorization", "Bearer keep-me");
        headers.add("X-Gateway-Token", "forged");
        EdgeFilterAccess.sanitize(headers, "cid-1", "real-attest");
        assertThat(headers.containsHeader("X-User-Id")).isFalse();
        assertThat(headers.containsHeader("x-user-role")).isFalse();
        assertThat(headers.containsHeader("X-Internal-Token")).isFalse();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer keep-me");
        assertThat(headers.getFirst(EdgeFilter.CORRELATION_HEADER)).isEqualTo("cid-1");
        // Security review F3: the gateway's own attestation replaces anything the client sent.
        assertThat(headers.get("X-Gateway-Token")).containsExactly("real-attest");
        HttpHeaders noToken = new HttpHeaders();
        noToken.add("X-Gateway-Token", "forged");
        EdgeFilterAccess.sanitize(noToken, "cid-2", "");
        assertThat(noToken.containsHeader("X-Gateway-Token")).isFalse();
    }

    @Test
    void bodiesOver1MbAreRejectedExceptOnTheResumeUploadRoute() {
        String bearer = "Bearer " + token("STUDENT", Instant.now().plusSeconds(600));
        byte[] body = new byte[1_500_000];
        client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(body).exchange()
                .expectStatus().isEqualTo(413);
        // Resume upload allows up to 2.2 MB; with no instances registered it reaches the load balancer (503, not 413).
        client.post().uri("/api/users/me/resume").header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream").bodyValue(body).exchange()
                .expectStatus().value(s -> assertThat(s).isNotEqualTo(413).isNotEqualTo(401));
        client.post().uri("/api/users/me/resume").header(HttpHeaders.AUTHORIZATION, bearer)
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream").bodyValue(new byte[2_400_000]).exchange()
                .expectStatus().isEqualTo(413);
    }

    @Test
    void gatewayHealthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().value(s -> assertThat(s).isNotEqualTo(401));
    }

    static String token(String role, Instant expiresAt) {
        return token(role, expiresAt, UUID.randomUUID().toString());
    }

    static String token(String role, Instant expiresAt, String subject) {
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(SECRET)).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("placehub-user-service")
                .subject(subject)
                .claim("role", role)
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
