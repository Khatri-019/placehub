package com.placehub.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Repositories live in {@code repository} and as nested interfaces in {@code policy.PolicyRepositories}. */
@Configuration
@EnableJpaRepositories(basePackages = "com.placehub.application", considerNestedRepositories = true)
public class JpaConfig {
}
