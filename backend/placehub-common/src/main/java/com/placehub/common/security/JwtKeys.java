package com.placehub.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Turns the configured JWT secret into an HMAC key, failing fast when it is too short. */
public final class JwtKeys {

    public static final int MIN_BYTES = 32;
    private static final String BASE64_PREFIX = "base64:";

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
        byte[] bytes = secret.startsWith(BASE64_PREFIX)
                ? Base64.getDecoder().decode(secret.substring(BASE64_PREFIX.length()))
                : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_BYTES + " bytes (got "
                    + bytes.length + ")");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
