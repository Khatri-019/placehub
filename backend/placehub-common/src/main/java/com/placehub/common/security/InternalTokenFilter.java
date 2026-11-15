package com.placehub.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Authenticates service-to-service calls on /internal/** via a shared token, compared in constant time. */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE = "INTERNAL";

    private final byte[] expected;
    private final JsonSecurityHandlers handlers;

    public InternalTokenFilter(String expectedToken, JsonSecurityHandlers handlers) {
        this.expected = expectedToken == null || expectedToken.isBlank()
                ? null : expectedToken.getBytes(StandardCharsets.UTF_8);
        this.handlers = handlers;
        if (JwtKeys.isLocalExampleValue(expectedToken)) {
            org.slf4j.LoggerFactory.getLogger(InternalTokenFilter.class).warn(
                    "INTERNAL_SERVICE_TOKEN is the local example value from .env.example; replace it outside a local demo");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expected == null || provided == null
                || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            handlers.write(response, request, HttpStatus.UNAUTHORIZED, "Missing or invalid internal token");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken("internal-service", null,
                AuthorityUtils.createAuthorityList("ROLE_" + ROLE));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
