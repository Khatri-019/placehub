package com.placehub.gateway.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Gateway copy of the JWT secret parsing (the reactive gateway does not depend on the servlet common module). */
public final class JwtKeys {

    public static final int MIN_BYTES = 32;

    private JwtKeys() {
    }

    public static SecretKey hmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured");
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
