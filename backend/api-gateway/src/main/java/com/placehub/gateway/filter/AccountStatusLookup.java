package com.placehub.gateway.filter;

import reactor.core.publisher.Mono;

/** Resolves the current account status ("ACTIVE", "DEACTIVATED", ...) of a user id; empty when unknown. */
@FunctionalInterface
public interface AccountStatusLookup {

    Mono<String> status(String userId);
}
