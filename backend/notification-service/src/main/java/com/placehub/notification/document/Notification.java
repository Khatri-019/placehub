package com.placehub.notification.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * In-app notification. Idempotency key is (eventId, recipientUserId), enforced by a unique index created in
 * {@link com.placehub.notification.config.IndexInitializer}. UUIDs are stored as strings.
 */
@Document(collection = Notification.COLLECTION)
public class Notification {

    public static final String COLLECTION = "notifications";

    @Id
    private String id;
    private String eventId;
    private String recipientUserId;
    private String type;
    private String title;
    private String message;
    private String relatedApplicationId;
    private String relatedJobId;
    private boolean read;
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(String eventId, String recipientUserId, String type, String title, String message,
                        String relatedApplicationId, String relatedJobId, Instant createdAt) {
        this.eventId = eventId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.relatedApplicationId = relatedApplicationId;
        this.relatedJobId = relatedJobId;
        this.read = false;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getRelatedApplicationId() {
        return relatedApplicationId;
    }

    public String getRelatedJobId() {
        return relatedJobId;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
