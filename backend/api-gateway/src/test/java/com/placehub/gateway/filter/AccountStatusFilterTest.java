package com.placehub.gateway.filter;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Security review F1: the status cache used by {@link AccountStatusFilter}. */
class AccountStatusFilterTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void deactivatedIsBlockedActiveAndUnknownPass() {
        AccountStatusCache cache = new AccountStatusCache(
                id -> Mono.just(id.equals("d") ? "DEACTIVATED" : "ACTIVE"), Duration.ofSeconds(15), Clock.systemUTC());
        StepVerifier.create(cache.isDeactivated("d")).expectNext(true).verifyComplete();
        StepVerifier.create(cache.isDeactivated("a")).expectNext(false).verifyComplete();
    }

    @Test
    void lookupFailuresFailOpenAndAreNotCached() {
        AtomicInteger calls = new AtomicInteger();
        AccountStatusCache cache = new AccountStatusCache(id -> {
            calls.incrementAndGet();
            return Mono.error(new IllegalStateException("user-service down"));
        }, Duration.ofSeconds(15), Clock.systemUTC());
        StepVerifier.create(cache.isDeactivated("x")).expectNext(false).verifyComplete();
        StepVerifier.create(cache.isDeactivated("x")).expectNext(false).verifyComplete();
        org.assertj.core.api.Assertions.assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void statusIsCachedForTheTtlThenRefreshed() {
        AtomicReference<String> status = new AtomicReference<>("ACTIVE");
        AtomicInteger calls = new AtomicInteger();
        MutableClock clock = new MutableClock();
        AccountStatusCache cache = new AccountStatusCache(id -> {
            calls.incrementAndGet();
            return Mono.just(status.get());
        }, Duration.ofSeconds(15), clock);

        StepVerifier.create(cache.isDeactivated("u")).expectNext(false).verifyComplete();
        status.set("DEACTIVATED");
        clock.now = clock.now.plusSeconds(10);
        StepVerifier.create(cache.isDeactivated("u")).expectNext(false).verifyComplete(); // still cached
        clock.now = clock.now.plusSeconds(6);
        StepVerifier.create(cache.isDeactivated("u")).expectNext(true).verifyComplete();
        org.assertj.core.api.Assertions.assertThat(calls.get()).isEqualTo(2);
    }
}
