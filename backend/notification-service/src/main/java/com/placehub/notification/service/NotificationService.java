package com.placehub.notification.service;

import com.placehub.common.error.ApiException;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import com.placehub.notification.document.Notification;
import com.placehub.notification.dto.NotificationResponse;
import com.placehub.notification.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Idempotent persistence plus recipient-scoped queries. Every query filters on recipientUserId = JWT sub. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final MongoTemplate mongo;
    private final EmailSender emailSender;

    public NotificationService(MongoTemplate mongo, EmailSender emailSender) {
        this.mongo = mongo;
        this.emailSender = emailSender;
    }

    /** @return how many notifications were newly stored (duplicates are skipped). */
    public int store(List<Notification> notifications) {
        int inserted = 0;
        for (Notification n : notifications) {
            try {
                mongo.insert(n);
                inserted++;
                emailSender.send(n.getRecipientUserId(), n.getTitle(), n.getMessage());
            } catch (DuplicateKeyException e) {
                log.info("Event {} already processed for recipient {}; skipping", n.getEventId(),
                        n.getRecipientUserId());
            }
        }
        return inserted;
    }

    public PageResponse<NotificationResponse> list(UUID recipient, boolean unreadOnly, Integer page, Integer size) {
        PageRequest pageable = Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Query query = mine(recipient, unreadOnly);
        long total = mongo.count(query, Notification.class);
        List<NotificationResponse> content = mongo.find(Query.of(query).with(pageable), Notification.class).stream()
                .map(NotificationResponse::from).toList();
        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    public long unreadCount(UUID recipient) {
        return mongo.count(mine(recipient, true), Notification.class);
    }

    /** 404 when the notification does not exist or belongs to someone else (no existence leak). */
    public NotificationResponse markRead(UUID recipient, String notificationId) {
        Query query = Query.query(Criteria.where("_id").is(notificationId)
                .and("recipientUserId").is(recipient.toString()));
        Notification updated = mongo.findAndModify(query, new Update().set("read", true),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                Notification.class);
        if (updated == null) {
            throw ApiException.notFound("Notification not found");
        }
        return NotificationResponse.from(updated);
    }

    public long markAllRead(UUID recipient) {
        return mongo.updateMulti(mine(recipient, true), new Update().set("read", true), Notification.class)
                .getModifiedCount();
    }

    private static Query mine(UUID recipient, boolean unreadOnly) {
        Criteria criteria = Criteria.where("recipientUserId").is(recipient.toString());
        if (unreadOnly) {
            criteria = criteria.and("read").is(false);
        }
        return Query.query(criteria);
    }
}
