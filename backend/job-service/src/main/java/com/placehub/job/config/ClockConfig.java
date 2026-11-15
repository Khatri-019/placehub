package com.placehub.job.config;

import com.placehub.job.service.EligibilityEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EligibilityEvaluator eligibilityEvaluator(Clock clock) {
        return new EligibilityEvaluator(clock);
    }
}
