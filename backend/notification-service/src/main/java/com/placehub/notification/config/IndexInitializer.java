package com.placehub.notification.config;

import com.placehub.notification.document.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the notification indexes explicitly at startup (auto-index-creation is not relied upon). Runs before the
 * Kafka listeners consume anything because listener containers start after singleton initialisation.
 */
@Component
public class IndexInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);
    private final MongoTemplate mongo;

    public IndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void afterPropertiesSet() {
        IndexOperations ops = mongo.indexOps(Notification.class);
        ops.createIndex(new Index().on("eventId", Sort.Direction.ASC).on("recipientUserId", Sort.Direction.ASC)
                .unique().named("uq_event_recipient"));
        ops.createIndex(new Index().on("recipientUserId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                .named("ix_recipient_created"));
        ops.createIndex(new Index().on("recipientUserId", Sort.Direction.ASC).on("read", Sort.Direction.ASC)
                .named("ix_recipient_read"));
        log.info("Notification indexes ensured");
    }
}
