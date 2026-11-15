package com.placehub.notification.service;

import com.placehub.notification.document.Notification;
import com.placehub.notification.messaging.ApplicationEvents;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFactoryTest {

    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    final UUID student = UUID.randomUUID();
    final UUID recruiter = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();

    ApplicationEvents.ApplicationStatusChanged changed(String newStatus) {
        return new ApplicationEvents.ApplicationStatusChanged(eventId, "APPLICATION_STATUS_CHANGED", 1, NOW,
                UUID.randomUUID(), UUID.randomUUID(), student, "Demo Student", recruiter,
                "Graduate Software Engineer", "Northstar", "APPLIED", newStatus, recruiter);
    }

    @Test
    void createdNotifiesStudentAndRecruiter() {
        var event = new ApplicationEvents.ApplicationCreated(eventId, "APPLICATION_CREATED", 1, NOW,
                UUID.randomUUID(), UUID.randomUUID(), student, "Demo Student", recruiter,
                "Graduate Software Engineer", "Northstar");
        List<Notification> list = NotificationFactory.forCreated(event, NOW);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getRecipientUserId()).isEqualTo(student.toString());
        assertThat(list.get(0).getTitle()).isEqualTo("Application submitted");
        assertThat(list.get(0).getType()).isEqualTo(NotificationFactory.TYPE_CREATED);
        assertThat(list.get(1).getRecipientUserId()).isEqualTo(recruiter.toString());
        assertThat(list.get(1).getTitle()).isEqualTo("New applicant for Graduate Software Engineer");
        assertThat(list).allMatch(n -> n.getEventId().equals(eventId.toString()) && !n.isRead());
    }

    @Test
    void withdrawalNotifiesRecruiterOnly() {
        List<Notification> list = NotificationFactory.forStatusChanged(changed("WITHDRAWN"), NOW);
        assertThat(list).singleElement().satisfies(n -> {
            assertThat(n.getRecipientUserId()).isEqualTo(recruiter.toString());
            assertThat(n.getTitle()).isEqualTo("Demo Student withdrew from Graduate Software Engineer");
            assertThat(n.getType()).isEqualTo(NotificationFactory.TYPE_WITHDRAWN);
        });
    }

    @Test
    void otherStatusChangesNotifyStudentOnly() {
        for (String status : new String[]{"UNDER_REVIEW", "SHORTLISTED", "SELECTED", "REJECTED"}) {
            assertThat(NotificationFactory.forStatusChanged(changed(status), NOW)).singleElement().satisfies(n -> {
                assertThat(n.getRecipientUserId()).isEqualTo(student.toString());
                assertThat(n.getTitle()).isEqualTo("Your application for Graduate Software Engineer is now " + status);
            });
        }
    }
}
