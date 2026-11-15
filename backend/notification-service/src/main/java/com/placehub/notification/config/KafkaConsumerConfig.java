package com.placehub.notification.config;

import com.placehub.notification.messaging.ApplicationEvents;
import com.placehub.notification.messaging.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;

/**
 * Consumer error handling (spec section 5.4): retry 3 times, 1 s apart, then publish to {@code <topic>.DLT}.
 * Invalid events are not retried. Topics and DLTs are created with 3 partitions (DLT partition = source partition).
 */
@Configuration
@EnableScheduling
public class KafkaConsumerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ApplicationEvents.DLT_SUFFIX, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    @Bean
    public NewTopic createdTopic() {
        return TopicBuilder.name(ApplicationEvents.CREATED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic statusChangedTopic() {
        return TopicBuilder.name(ApplicationEvents.STATUS_CHANGED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic placementTopic() {
        return TopicBuilder.name(ApplicationEvents.PLACEMENT_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic placementDeadLetterTopic() {
        return TopicBuilder.name(ApplicationEvents.PLACEMENT_TOPIC + ApplicationEvents.DLT_SUFFIX).partitions(3)
                .replicas(1).build();
    }

    @Bean
    public NewTopic createdDeadLetterTopic() {
        return TopicBuilder.name(ApplicationEvents.CREATED_TOPIC + ApplicationEvents.DLT_SUFFIX).partitions(3)
                .replicas(1).build();
    }

    @Bean
    public NewTopic statusChangedDeadLetterTopic() {
        return TopicBuilder.name(ApplicationEvents.STATUS_CHANGED_TOPIC + ApplicationEvents.DLT_SUFFIX).partitions(3)
                .replicas(1).build();
    }
}
