package com.placehub.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Emits one startup line with service name, active profiles and port (spec section 14). */
@Component
public class StartupLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);
    private final Environment environment;

    public StartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        log.info("PlaceHub service '{}' started with profiles {} on port {}",
                environment.getProperty("spring.application.name"),
                Arrays.toString(environment.getActiveProfiles()), event.getWebServer().getPort());
    }
}
