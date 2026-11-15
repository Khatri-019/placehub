package com.placehub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Security settings shared by all business services.
 *
 * @param jwtSecret     HS256 secret (raw UTF-8, or "base64:..." for Base64); at least 32 bytes
 * @param jwtIssuer     expected {@code iss} claim
 * @param internalToken shared secret for {@code /internal/**}; blank means internal routes always reject
 * @param publicPaths   extra unauthenticated path patterns for this service
 */
@ConfigurationProperties(prefix = "placehub.security")
public record PlaceHubSecurityProperties(String jwtSecret, String jwtIssuer, String internalToken,
                                         List<String> publicPaths) {

    public static final String DEFAULT_ISSUER = "placehub-user-service";

    public PlaceHubSecurityProperties {
        jwtIssuer = jwtIssuer == null || jwtIssuer.isBlank() ? DEFAULT_ISSUER : jwtIssuer;
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
