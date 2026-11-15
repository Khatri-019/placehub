package com.placehub.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Starts the Kafka listener containers once the broker's bootstrap host resolves.
 * <p>
 * Kafka clients throw {@code ConfigException: No resolvable bootstrap urls} from their constructors when the broker
 * hostname does not resolve (for example, the kafka container is stopped). With auto-startup that exception aborts the
 * whole application context. Listener auto-startup is therefore disabled
 * ({@code spring.kafka.listener.auto-startup=false}) and this component starts the containers as soon as the host
 * resolves, ensuring the topics exist first. Once started, the consumers reconnect on their own after broker outages.
 */
@Component
public class KafkaListenerStarter {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerStarter.class);

    private final KafkaListenerEndpointRegistry registry;
    private final KafkaAdmin kafkaAdmin;
    private final List<String> bootstrapServers;
    private volatile boolean started;

    public KafkaListenerStarter(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin, KafkaProperties props) {
        this.registry = registry;
        this.kafkaAdmin = kafkaAdmin;
        this.bootstrapServers = props.getBootstrapServers();
    }

    @Scheduled(initialDelay = 0, fixedDelay = 5000)
    public void startWhenBrokerResolvable() {
        if (started) {
            return;
        }
        if (!anyBootstrapHostResolves(bootstrapServers)) {
            log.warn("Kafka bootstrap servers {} not resolvable yet; listeners will start when they are",
                    bootstrapServers);
            return;
        }
        try {
            kafkaAdmin.initialize();
            registry.start();
            started = true;
            log.info("Kafka listener containers started");
        } catch (RuntimeException e) {
            log.warn("Could not start Kafka listeners yet: {}", e.toString());
        }
    }

    public boolean isStarted() {
        return started;
    }

    static boolean anyBootstrapHostResolves(List<String> servers) {
        for (String server : servers) {
            String host = server.contains(":") ? server.substring(0, server.lastIndexOf(':')) : server;
            try {
                InetAddress.getByName(host);
                return true;
            } catch (UnknownHostException e) {
                // try the next one
            }
        }
        return false;
    }
}
