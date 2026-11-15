package com.placehub.notification.service;

import com.placehub.common.error.ApiException;
import com.placehub.notification.document.Notification;
import com.placehub.notification.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    MongoTemplate mongo = mock(MongoTemplate.class);
    EmailSender email = mock(EmailSender.class);
    NotificationService service = new NotificationService(mongo, email);

    static Notification n(String recipient) {
        return new Notification(UUID.randomUUID().toString(), recipient, "T", "title", "msg", "a", "j",
                Instant.now());
    }

    @Test
    void duplicateEventProducesNoSecondInsertOrEmail() {
        Notification first = n("u1");
        when(mongo.insert(first)).thenReturn(first).thenThrow(new DuplicateKeyException("E11000"));
        assertThat(service.store(List.of(first))).isEqualTo(1);
        assertThat(service.store(List.of(first))).isZero();
        verify(email, times(1)).send(eq("u1"), anyString(), anyString());
    }

    @Test
    void oneDuplicateRecipientDoesNotBlockTheOther() {
        Notification student = n("student");
        Notification recruiter = n("recruiter");
        when(mongo.insert(student)).thenThrow(new DuplicateKeyException("E11000"));
        when(mongo.insert(recruiter)).thenReturn(recruiter);
        assertThat(service.store(List.of(student, recruiter))).isEqualTo(1);
        verify(email, never()).send(eq("student"), anyString(), anyString());
    }

    @Test
    void markReadIsScopedToRecipientAnd404sOtherwise() {
        UUID me = UUID.randomUUID();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Notification.class))).thenReturn(null);
        assertThatThrownBy(() -> service.markRead(me, "someone-elses-id"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class),
                eq(Notification.class));
        assertThat(query.getValue().getQueryObject().get("recipientUserId")).isEqualTo(me.toString());
    }

    @Test
    void listAlwaysFiltersByRecipient() {
        UUID me = UUID.randomUUID();
        service.unreadCount(me);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(query.capture(), eq(Notification.class));
        assertThat(query.getValue().getQueryObject().get("recipientUserId")).isEqualTo(me.toString());
        assertThat(query.getValue().getQueryObject().get("read")).isEqualTo(false);
    }
}
