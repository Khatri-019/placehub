package com.placehub.gateway.filter;

import com.placehub.gateway.config.GatewayProperties;
import com.placehub.gateway.security.ErrorWriter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;

/**
 * Rejects requests carrying a valid token of a DEACTIVATED account (security review F1). Runs after Spring Security
 * (order -100) has authenticated the JWT, and before routing.
 */
@Component
@Order(0)
public class AccountStatusFilter implements WebFilter {

    static final String MESSAGE = "Your account is deactivated";

    private final AccountStatusCache cache;
    private final ErrorWriter errorWriter;

    public AccountStatusFilter(AccountStatusLookup lookup, ErrorWriter errorWriter, GatewayProperties props) {
        this.cache = new AccountStatusCache(lookup, Duration.ofSeconds(props.accountStatusTtlSeconds()), Clock.systemUTC());
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(p -> ((JwtAuthenticationToken) p).getToken().getSubject())
                .flatMap(cache::isDeactivated)
                .defaultIfEmpty(false)
                .flatMap(deactivated -> deactivated
                        ? errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, MESSAGE)
                        : chain.filter(exchange));
    }
}
