package com.placehub.common;

import com.placehub.common.error.ApiException;
import com.placehub.common.paging.Paging;
import com.placehub.common.security.JwtKeys;
import com.placehub.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonUtilitiesTest {

    private static final Map<String, String> FIELDS = Map.of("createdAt", "createdAt", "deadline", "deadline");

    @Test
    void pagingClampsSizeAndDefaultsPage() {
        PageRequest p = Paging.of(-3, 500, null, FIELDS, Sort.by("createdAt"));
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(Paging.MAX_SIZE);
        assertThat(Paging.of(null, null, null, FIELDS, Sort.unsorted()).getPageSize()).isEqualTo(20);
    }

    @Test
    void pagingParsesWhitelistedSort() {
        PageRequest p = Paging.of(1, 10, "deadline,desc", FIELDS, Sort.unsorted());
        assertThat(p.getSort().getOrderFor("deadline").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pagingRejectsUnknownSortField() {
        assertThatThrownBy(() -> Paging.of(0, 10, "passwordHash,asc", FIELDS, Sort.unsorted()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported sort field");
        assertThatThrownBy(() -> Paging.of(0, 10, "deadline,sideways", FIELDS, Sort.unsorted()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void jwtKeyRejectsShortSecrets() {
        assertThatThrownBy(() -> JwtKeys.hmacKey("too-short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtKeys.hmacKey(" ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtKeyAcceptsRawAndBase64() {
        assertThat(JwtKeys.hmacKey("change-me-local-dev-secret-at-least-32-bytes!!").getEncoded()).hasSizeGreaterThan(31);
        String b64 = "base64:" + Base64.getEncoder().encodeToString(new byte[32]);
        assertThat(JwtKeys.hmacKey(b64).getEncoded()).hasSize(32);
    }

    @Test
    void correlationIdIsSanitized() {
        assertThat(CorrelationIdFilter.sanitize("abc-123")).isEqualTo("abc-123");
        assertThat(CorrelationIdFilter.sanitize("bad value\r\ninjected")).isNotEqualTo("bad value\r\ninjected")
                .hasSize(36);
        assertThat(CorrelationIdFilter.sanitize(null)).hasSize(36);
    }
}
