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

/** Reports Kafka broker reachability in /actuator/health (spec section 10.8). */
@Component("kafka")
public class KafkaHealthIndicator extends AbstractHealthIndicator implements AutoCloseable {

    private final AdminClient adminClient;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        this.adminClient = AdminClient.create(config);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        var cluster = adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(3000));
        String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);
        int nodes = cluster.nodes().get(3, TimeUnit.SECONDS).size();
        builder.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes);
    }

    @Override
    public void close() {
        adminClient.close();
    }
}
