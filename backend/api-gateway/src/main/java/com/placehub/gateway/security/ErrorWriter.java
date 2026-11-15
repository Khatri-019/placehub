package com.placehub.gateway.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.placehub.gateway.filter.EdgeFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/** Writes the standard PlaceHub error body from reactive code. */
@Component
public class ErrorWriter {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GatewayError(Instant timestamp, int status, String error, String message, String path,
                        String correlationId) {
    }

    private final JsonMapper jsonMapper;

    public ErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        GatewayError body = new GatewayError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                exchange.getRequest().getPath().value(), exchange.getAttribute(EdgeFilter.CORRELATION_HEADER));
        DataBuffer buffer = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}
