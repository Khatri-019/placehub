package com.placehub.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code placehub.jwt.ttl-minutes}: access token lifetime (default 60). */
@ConfigurationProperties(prefix = "placehub.jwt")
public record JwtProperties(Integer ttlMinutes) {

    public JwtProperties {
        ttlMinutes = ttlMinutes == null || ttlMinutes < 1 ? 60 : ttlMinutes;
    }
}
