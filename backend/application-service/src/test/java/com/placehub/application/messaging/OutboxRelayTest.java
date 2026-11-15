package com.placehub.application.messaging;

import com.placehub.application.entity.OutboxEvent;
import com.placehub.application.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    OutboxRepository repo = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    TransactionTemplate tx = mock(TransactionTemplate.class);
    OutboxRelay relay = new OutboxRelay(repo, kafka, tx, Clock.fixed(NOW, ZoneOffset.UTC));

    @SuppressWarnings("unchecked")
    void runTransactionsInline() {
        when(tx.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
    }

    static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), ApplicationEvents.CREATED,
                ApplicationEvents.CREATED_TOPIC, "{}", "cid-1", NOW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesKeyedByAggregateWithCorrelationHeaderAndMarksSent() {
        runTransactionsInline();
        OutboxEvent e = event();
        when(repo.lockDueBatch(NOW, OutboxRelay.BATCH_SIZE)).thenReturn(List.of(e));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(
                mock(SendResult.class)));

        assertThat(relay.relayBatch()).isEqualTo(1);
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo(e.getAggregateId().toString());
        assertThat(captor.getValue().topic()).isEqualTo(ApplicationEvents.CREATED_TOPIC);
        assertThat(captor.getValue().headers().lastHeader("X-Correlation-Id").value()).isEqualTo("cid-1".getBytes());
        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(e.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failureRecordsAttemptBacksOffAndStopsTheBatch() {
        runTransactionsInline();
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repo.lockDueBatch(any(), anyInt())).thenReturn(List.of(first, second));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThat(relay.relayBatch()).isZero();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker down");
        assertThat(first.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(first.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(second.getAttempts()).isZero();
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void markedFailedAfterMaxAttempts() {
        OutboxEvent e = event();
        for (int i = 1; i < OutboxRelay.MAX_ATTEMPTS; i++) {
            assertThat(e.markAttemptFailed("x", NOW, OutboxRelay.MAX_ATTEMPTS)).isFalse();
        }
        assertThat(e.markAttemptFailed("x", NOW, OutboxRelay.MAX_ATTEMPTS)).isTrue();
        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
    }

    @Test
    void backoffIsExponentialAndCapped() {
        assertThat(OutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxRelay.backoff(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxRelay.backoff(9)).isEqualTo(Duration.ofSeconds(60));
    }
}
