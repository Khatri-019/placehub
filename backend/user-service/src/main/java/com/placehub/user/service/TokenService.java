package com.placehub.user.service;

import com.placehub.common.security.PlaceHubSecurityProperties;
import com.placehub.user.config.JwtProperties;
import com.placehub.user.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Issues HS256 access tokens: sub, email, role, name, iss, iat, exp. */
@Service
public class TokenService {

    public record IssuedToken(String value, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final String issuer;

    public TokenService(JwtEncoder encoder, Clock clock, JwtProperties props,
                        PlaceHubSecurityProperties securityProps) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(props.ttlMinutes());
        this.issuer = securityProps.jwtIssuer();
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }
}
