package com.placehub.common.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Turns the configured JWT secret into an HMAC key, failing fast when it is too short. */
public final class JwtKeys {

    public static final int MIN_BYTES = 32;
    private static final String BASE64_PREFIX = "base64:";

    private JwtKeys() {
    }

    public static SecretKey hmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured");
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
