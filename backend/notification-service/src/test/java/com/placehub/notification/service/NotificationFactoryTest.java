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
        return changed(newStatus, "USER");
    }

    ApplicationEvents.ApplicationStatusChanged changed(String newStatus, String actorType) {
        return new ApplicationEvents.ApplicationStatusChanged(eventId, "APPLICATION_STATUS_CHANGED", 1, NOW,
                UUID.randomUUID(), UUID.randomUUID(), student, "Demo Student", recruiter,
                "Graduate Software Engineer", "Northstar", "APPLIED", newStatus,
                "SYSTEM".equals(actorType) ? null : recruiter, actorType, "SYSTEM".equals(actorType)
                ? "Withdrawn by placement policy: student placed" : null);
    }

    @Test
    void createdNotifiesStudentAndRecruiter() {
        var event = new ApplicationEvents.ApplicationCreated(eventId, "APPLICATION_CREATED", 1, NOW,
                UUID.randomUUID(), UUID.randomUUID(), student, "Demo Student", recruiter,
                "Graduate Software Engineer", "Northstar", null);
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
    void systemEventsUseTheirOwnWording() {
        assertThat(NotificationFactory.forStatusChanged(changed("SHORTLISTED", "SYSTEM"), NOW)).singleElement()
                .satisfies(n -> {
                    assertThat(n.getRecipientUserId()).isEqualTo(student.toString());
                    assertThat(n.getTitle()).isEqualTo(
                            "You were automatically shortlisted for Graduate Software Engineer at Northstar");
                    assertThat(n.getType()).isEqualTo(NotificationFactory.TYPE_AUTO_SHORTLISTED);
                });
        assertThat(NotificationFactory.forStatusChanged(changed("WITHDRAWN", "SYSTEM"), NOW)).singleElement()
                .satisfies(n -> {
                    assertThat(n.getRecipientUserId()).isEqualTo(recruiter.toString());
                    assertThat(n.getTitle()).isEqualTo(
                            "Demo Student was withdrawn from Graduate Software Engineer by placement policy");
                });
        var created = new ApplicationEvents.ApplicationCreated(eventId, "APPLICATION_CREATED", 1, NOW,
                UUID.randomUUID(), UUID.randomUUID(), student, "Demo Student", recruiter, "Quant Developer",
                "Meridian", true);
        assertThat(NotificationFactory.forCreated(created, NOW).get(1).getTitle())
                .isEqualTo("New applicant for Quant Developer (auto-shortlisted)");
    }

    @Test
    void placementNotifiesTheStudent() {
        var e = new ApplicationEvents.StudentPlacementRecorded(eventId, "STUDENT_PLACEMENT_RECORDED", 1, NOW,
                UUID.randomUUID(), student, "Demo Student", null, "Meridian", new java.math.BigDecimal("20"), true,
                "OFF_CAMPUS");
        assertThat(NotificationFactory.forPlacement(e, NOW)).singleElement().satisfies(n -> {
            assertThat(n.getRecipientUserId()).isEqualTo(student.toString());
            assertThat(n.getTitle()).isEqualTo("Placement recorded: Meridian at 20.00 LPA");
            assertThat(n.getMessage()).contains("locked");
        });
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
