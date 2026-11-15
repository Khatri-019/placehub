package com.placehub.gateway.filter;

import com.placehub.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/** Looks the status up on the User Service internal API (service-to-service, X-Internal-Token). */
@Configuration
public class UserServiceAccountStatusLookup {

    private static final Logger log = LoggerFactory.getLogger(UserServiceAccountStatusLookup.class);

    @Bean
    AccountStatusLookup accountStatusLookup(ReactorLoadBalancerExchangeFilterFunction loadBalancer, GatewayProperties props) {
        if (props.internalToken() == null || props.internalToken().isBlank()) {
            log.warn("INTERNAL_SERVICE_TOKEN is not set: deactivated accounts keep access until their token expires");
            return userId -> Mono.empty();
        }
        // A private client: a shared @LoadBalanced WebClient.Builder bean would replace Boot's default one.
        WebClient client = WebClient.builder().filter(loadBalancer).baseUrl("http://user-service").build();
        return userId -> client.get().uri("/internal/users/{id}", userId)
                .header("X-Internal-Token", props.internalToken())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(2))
                .map(body -> String.valueOf(body.get("status")))
                // A deleted/unknown user cannot hold a valid account; treat it like a deactivated one.
                .onErrorResume(WebClientResponseException.class, ex -> ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                        ? Mono.just("DEACTIVATED") : Mono.error(ex));
    }
}
