package com.placehub.notification.service;

import com.placehub.notification.document.Notification;
import com.placehub.notification.messaging.ApplicationEvents;

import java.time.Instant;
import java.util.List;

/**
 * Decides recipients (spec section 5.4): the producer never chooses UI recipients.
 * <ul>
 *   <li>APPLICATION_CREATED: student ("Application submitted") and recruiter ("New applicant for {jobTitle}")</li>
 *   <li>STATUS_CHANGED to WITHDRAWN: recruiter ("{studentName} withdrew from {jobTitle}")</li>
 *   <li>STATUS_CHANGED otherwise: student ("Your application for {jobTitle} is now {newStatus}")</li>
 * </ul>
 */
public final class NotificationFactory {

    public static final String TYPE_CREATED = "APPLICATION_CREATED";
    public static final String TYPE_NEW_APPLICANT = "NEW_APPLICANT";
    public static final String TYPE_STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";
    public static final String TYPE_WITHDRAWN = "APPLICATION_WITHDRAWN";

    private NotificationFactory() {
    }

    public static List<Notification> forCreated(ApplicationEvents.ApplicationCreated e, Instant now) {
        String eventId = e.eventId().toString();
        String appId = e.applicationId().toString();
        String jobId = e.jobId().toString();
        String company = e.companyName() == null ? "" : " at " + e.companyName();
        return List.of(
                new Notification(eventId, e.studentUserId().toString(), TYPE_CREATED, "Application submitted",
                        "Your application for " + e.jobTitle() + company + " was submitted.", appId, jobId, now),
                new Notification(eventId, e.recruiterUserId().toString(), TYPE_NEW_APPLICANT,
                        "New applicant for " + e.jobTitle(),
                        name(e.studentName()) + " applied to " + e.jobTitle() + ".", appId, jobId, now));
    }

    public static List<Notification> forStatusChanged(ApplicationEvents.ApplicationStatusChanged e, Instant now) {
        String eventId = e.eventId().toString();
        String appId = e.applicationId().toString();
        String jobId = e.jobId().toString();
        if ("WITHDRAWN".equals(e.newStatus())) {
            String title = name(e.studentName()) + " withdrew from " + e.jobTitle();
            return List.of(new Notification(eventId, e.recruiterUserId().toString(), TYPE_WITHDRAWN, title,
                    title + ".", appId, jobId, now));
        }
        String title = "Your application for " + e.jobTitle() + " is now " + e.newStatus();
        return List.of(new Notification(eventId, e.studentUserId().toString(), TYPE_STATUS_CHANGED, title,
                title + ".", appId, jobId, now));
    }

    private static String name(String studentName) {
        return studentName == null || studentName.isBlank() ? "A student" : studentName;
    }
}
