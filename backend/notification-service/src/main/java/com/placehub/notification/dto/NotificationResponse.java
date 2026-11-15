package com.placehub.notification.dto;

import com.placehub.notification.document.Notification;

import java.time.Instant;

public record NotificationResponse(String id, String eventId, String recipientUserId, String type, String title,
                                   String message, String relatedApplicationId, String relatedJobId, boolean read,
                                   Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventId(), n.getRecipientUserId(), n.getType(), n.getTitle(),
                n.getMessage(), n.getRelatedApplicationId(), n.getRelatedJobId(), n.isRead(), n.getCreatedAt());
    }
}
