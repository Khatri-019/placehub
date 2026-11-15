package com.placehub.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived cache of account statuses (security review F1). A deactivated account is cut off within the TTL instead
 * of when its token expires. Lookup failures fail open (the services still validate the JWT, and sign-in itself needs
 * the User Service) and are not cached, so the next request retries.
 */
public class AccountStatusCache {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusCache.class);
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(boolean deactivated, Instant expiresAt) {
    }

    private final AccountStatusLookup lookup;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public AccountStatusCache(AccountStatusLookup lookup, Duration ttl, Clock clock) {
        this.lookup = lookup;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Mono<Boolean> isDeactivated(String userId) {
        Instant now = clock.instant();
        Entry cached = entries.get(userId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Mono.just(cached.deactivated());
        }
        return lookup.status(userId)
                .map(status -> {
                    boolean deactivated = "DEACTIVATED".equals(status);
                    if (entries.size() >= MAX_ENTRIES) {
                        entries.clear();
                    }
                    entries.put(userId, new Entry(deactivated, now.plus(ttl)));
                    return deactivated;
                })
                .onErrorResume(ex -> {
                    log.warn("Account status lookup failed for {}; allowing the request: {}", userId, ex.toString());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }
}
