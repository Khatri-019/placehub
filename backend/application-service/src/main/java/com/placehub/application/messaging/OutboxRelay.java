package com.placehub.application.messaging;

import com.placehub.application.entity.OutboxEvent;
import com.placehub.application.repository.OutboxRepository;
import com.placehub.common.web.CorrelationIdFilter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes PENDING outbox rows to Kafka (at-least-once). A crash between send and commit causes a resend; the
 * consumer deduplicates by eventId. Failed sends back off exponentially (2^attempts s, max 60 s) and are marked
 * FAILED after {@link #MAX_ATTEMPTS}. The batch stops at the first failure: the broker is most likely down.
 */
@Component
public class OutboxRelay {

    public static final int BATCH_SIZE = 50;
    public static final int MAX_ATTEMPTS = 10;
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                       Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void scheduledRelay() {
        try {
            relayBatch();
        } catch (RuntimeException e) {
            log.warn("Outbox relay iteration failed: {}", e.toString());
        }
    }

    /** @return number of rows published in this iteration */
    public int relayBatch() {
        Integer sent = tx.execute(status -> {
            List<OutboxEvent> batch = outbox.lockDueBatch(clock.instant(), BATCH_SIZE);
            int published = 0;
            for (OutboxEvent event : batch) {
                try {
                    publish(event);
                    event.markSent(clock.instant());
                    published++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    Instant next = clock.instant().plus(backoff(event.getAttempts() + 1));
                    boolean failed = event.markAttemptFailed(cause.toString(), next, MAX_ATTEMPTS);
                    if (failed) {
                        log.error("Outbox event {} ({}) FAILED after {} attempts: {}", event.getId(),
                                event.getEventType(), event.getAttempts(), cause.toString());
                    } else {
                        log.warn("Outbox event {} attempt {} failed, retry at {}: {}", event.getId(),
                                event.getAttempts(), next, cause.toString());
                    }
                    break;
                }
            }
            return published;
        });
        return sent == null ? 0 : sent;
    }

    private void publish(OutboxEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), null,
                event.getAggregateId().toString(), event.getPayload());
        if (event.getCorrelationId() != null) {
            record.headers().add(new RecordHeader(CorrelationIdFilter.HEADER,
                    event.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
        }
        record.headers().add(new RecordHeader("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8)));
        kafka.send(record).get(5, TimeUnit.SECONDS);
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(60, 1L << Math.min(attempt, 6));
        return Duration.ofSeconds(seconds);
    }
}
