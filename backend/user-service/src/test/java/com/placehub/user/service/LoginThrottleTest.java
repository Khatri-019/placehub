package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Security review F5: failed sign-ins are throttled per email (known or not, so it reveals nothing). */
class LoginThrottleTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterTheLimitUntilTheWindowEnds() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock, 10, java.time.Duration.ofMinutes(15));
        for (int i = 0; i < 10; i++) {
            throttle.checkAllowed("a@x.com");
            throttle.recordFailure("a@x.com");
        }
        assertThatThrownBy(() -> throttle.checkAllowed("a@x.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessageContaining("Too many failed sign-in attempts");
        assertThatCode(() -> throttle.checkAllowed("b@x.com")).doesNotThrowAnyException();
        clock.now = clock.now.plusSeconds(15 * 60 + 1);
        assertThatCode(() -> throttle.checkAllowed("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounter() {
        LoginThrottle throttle = new LoginThrottle(new MutableClock(), 3, java.time.Duration.ofMinutes(15));
        throttle.recordFailure("a@x.com");
        throttle.recordFailure("a@x.com");
        throttle.recordSuccess("a@x.com");
        throttle.recordFailure("a@x.com");
        throttle.recordFailure("a@x.com");
        assertThatCode(() -> throttle.checkAllowed("a@x.com")).doesNotThrowAnyException();
    }
}
