package com.placehub.user.service;

import com.placehub.common.security.JwtKeys;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Security review F2: the committed example secrets are recognised so services warn loudly when they are in use. */
class ExampleSecretsTest {

    @Test
    void committedExampleSecretsAreRecognised() throws Exception {
        Path env = Path.of("..", "..", ".env.example");
        List<String> secrets = Files.readAllLines(env).stream()
                .filter(l -> l.startsWith("JWT_SECRET=") || l.startsWith("INTERNAL_SERVICE_TOKEN="))
                .map(l -> l.substring(l.indexOf('=') + 1))
                .toList();
        assertThat(secrets).hasSize(2).allMatch(JwtKeys::isLocalExampleValue);
        assertThat(JwtKeys.isLocalExampleValue("base64:c2VjcmV0LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzMjU2IQ==")).isFalse();
        assertThat(JwtKeys.isLocalExampleValue(null)).isFalse();
    }
}
