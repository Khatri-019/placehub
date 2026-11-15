package com.placehub.notification.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaListenerStarterTest {

    @Test
    void detectsResolvableBootstrapHosts() {
        assertThat(KafkaListenerStarter.anyBootstrapHostResolves(List.of("localhost:29092"))).isTrue();
        assertThat(KafkaListenerStarter.anyBootstrapHostResolves(
                List.of("no-such-host.placehub.invalid:9092", "localhost:9092"))).isTrue();
    }

    @Test
    void unresolvableHostsAreReportedSoStartupIsDeferred() {
        assertThat(KafkaListenerStarter.anyBootstrapHostResolves(List.of("no-such-host.placehub.invalid:9092")))
                .isFalse();
    }
}
