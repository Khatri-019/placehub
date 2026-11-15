package com.placehub.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Gateway copy of the JWT secret parsing (the reactive gateway does not depend on the servlet common module). */
public final class JwtKeys {

    public static final int MIN_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);
    /** Every value in the committed .env.example starts with this prefix (local demo only). */
    public static final String EXAMPLE_PREFIX = "change-me-local";

    private JwtKeys() {
    }

    /** True for the committed local example values, which must never be used outside a local demo. */
    public static boolean isLocalExampleValue(String value) {
        return value != null && value.startsWith(EXAMPLE_PREFIX);
    }

    public static SecretKey hmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured");
        }
        if (isLocalExampleValue(secret)) {
            log.warn("JWT_SECRET is the local example value from .env.example: anyone can mint tokens. "
                    + "Use it only for a local demo and replace it everywhere else.");
        }
        byte[] bytes = secret.startsWith("base64:")
                ? Base64.getDecoder().decode(secret.substring("base64:".length()))
                : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_BYTES + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
