package com.placehub.gateway.filter;

import com.placehub.gateway.security.ErrorWriter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter at the edge (runs before Spring Security):
 * rejects /internal/** with 404, strips spoofable identity/internal headers, and propagates X-Correlation-Id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EdgeFilter implements WebFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    /** Attests to the services that a request came through the gateway (security review F3). */
    public static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ErrorWriter errorWriter;
    private final String gatewayToken;

    public EdgeFilter(ErrorWriter errorWriter, com.placehub.gateway.config.GatewayProperties props) {
        this.errorWriter = errorWriter;
        this.gatewayToken = props.internalToken();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        String correlationId = incoming != null && SAFE.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
        exchange.getAttributes().put(CORRELATION_HEADER, correlationId);

        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, "Resource not found");
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> sanitize(h, correlationId, gatewayToken))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    static void sanitize(HttpHeaders headers, String correlationId, String gatewayToken) {
        List<String> spoofable = headers.headerNames().stream()
                .filter(n -> n.regionMatches(true, 0, "X-User-", 0, 7) || n.equalsIgnoreCase(INTERNAL_TOKEN_HEADER)
                        || n.equalsIgnoreCase(GATEWAY_TOKEN_HEADER))
                .toList();
        spoofable.forEach(headers::remove);
        headers.set(CORRELATION_HEADER, correlationId);
        if (gatewayToken != null && !gatewayToken.isBlank()) {
            headers.set(GATEWAY_TOKEN_HEADER, gatewayToken);
        }
    }
}
