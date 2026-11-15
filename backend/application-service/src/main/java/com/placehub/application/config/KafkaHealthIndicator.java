package com.placehub.application.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reports Kafka broker reachability in /actuator/health (not in liveness or readiness). The admin client is created
 * lazily: when the broker's hostname does not resolve (container stopped), creating a Kafka client throws, and that
 * must never prevent the service from starting.
 */
@Component("kafka")
public class KafkaHealthIndicator extends AbstractHealthIndicator implements AutoCloseable {

    private final Map<String, Object> config;
    private volatile AdminClient adminClient;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        this.config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        this.config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        var cluster = admin().describeCluster(new DescribeClusterOptions().timeoutMs(3000));
        String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);
        int nodes = cluster.nodes().get(3, TimeUnit.SECONDS).size();
        builder.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes);
    }

    private AdminClient admin() {
        AdminClient current = adminClient;
        if (current == null) {
            synchronized (this) {
                if (adminClient == null) {
                    adminClient = AdminClient.create(config);
                }
                current = adminClient;
            }
        }
        return current;
    }

    @Override
    public void close() {
        AdminClient current = adminClient;
        if (current != null) {
            current.close();
        }
    }
}
