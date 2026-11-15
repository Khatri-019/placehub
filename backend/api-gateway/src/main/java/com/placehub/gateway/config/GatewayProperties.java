package com.placehub.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret      same HS256 secret the User Service signs with
 * @param jwtIssuer      expected issuer claim
 * @param frontendOrigin the only origin allowed by CORS
 */
@ConfigurationProperties(prefix = "placehub.gateway")
public record GatewayProperties(String jwtSecret, String jwtIssuer, String frontendOrigin) {

    public GatewayProperties {
        jwtIssuer = jwtIssuer == null || jwtIssuer.isBlank() ? "placehub-user-service" : jwtIssuer;
        frontendOrigin = frontendOrigin == null || frontendOrigin.isBlank() ? "http://localhost:5173" : frontendOrigin;
    }
}
