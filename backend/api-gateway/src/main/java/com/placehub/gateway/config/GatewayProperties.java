package com.placehub.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret      same HS256 secret the User Service signs with
 * @param jwtIssuer      expected issuer claim
 * @param frontendOrigin the only origin allowed by CORS
 * @param internalToken  X-Internal-Token for the User Service status lookup (security review F1)
 * @param accountStatusTtlSeconds how long an account status is cached (bounds how late a deactivation applies)
 */
@ConfigurationProperties(prefix = "placehub.gateway")
public record GatewayProperties(String jwtSecret, String jwtIssuer, String frontendOrigin, String internalToken,
                                Integer accountStatusTtlSeconds) {

    public GatewayProperties {
        jwtIssuer = jwtIssuer == null || jwtIssuer.isBlank() ? "placehub-user-service" : jwtIssuer;
        frontendOrigin = frontendOrigin == null || frontendOrigin.isBlank() ? "http://localhost:5173" : frontendOrigin;
        accountStatusTtlSeconds = accountStatusTtlSeconds == null || accountStatusTtlSeconds < 1 ? 15 : accountStatusTtlSeconds;
    }
}
