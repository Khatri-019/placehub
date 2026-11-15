package com.placehub.application.config;

import com.placehub.application.messaging.ApplicationEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class MessagingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public NewTopic applicationCreatedTopic() {
        return TopicBuilder.name(ApplicationEvents.CREATED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic applicationStatusChangedTopic() {
        return TopicBuilder.name(ApplicationEvents.STATUS_CHANGED_TOPIC).partitions(3).replicas(1).build();
    }
}
