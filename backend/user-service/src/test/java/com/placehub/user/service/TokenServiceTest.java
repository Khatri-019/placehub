package com.placehub.user.service;

import com.placehub.common.security.CommonSecurityConfiguration;
import com.placehub.common.security.JwtKeys;
import com.placehub.common.security.PlaceHubSecurityProperties;
import com.placehub.user.config.JwtProperties;
import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Role;
import com.placehub.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes!";
    final PlaceHubSecurityProperties props = new PlaceHubSecurityProperties(SECRET, null, null, List.of());
    final JwtDecoder decoder = new CommonSecurityConfiguration().jwtDecoder(props);
    final User user = new User(UUID.randomUUID(), "Asha Rao", "asha@example.com", "hash", Role.STUDENT,
            AccountStatus.ACTIVE);

    TokenService service(Clock clock, int ttl) {
        return new TokenService(NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(SECRET)).build(), clock,
                new JwtProperties(ttl), props);
    }

    @Test
    void tokenCarriesRequiredClaims() {
        Instant now = Instant.now();
        TokenService.IssuedToken token = service(Clock.fixed(now, ZoneOffset.UTC), 60).issue(user);
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("asha@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Asha Rao");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("placehub-user-service");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant longAgo = Instant.now().minus(Duration.ofHours(3));
        String token = service(Clock.fixed(longAgo, ZoneOffset.UTC), 60).issue(user).value();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String other = "a-completely-different-secret-of-32-bytes+";
        String token = new TokenService(NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(other)).build(),
                Clock.systemUTC(), new JwtProperties(60), props).issue(user).value();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
