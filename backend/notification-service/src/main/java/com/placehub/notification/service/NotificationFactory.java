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
    public static final String TYPE_AUTO_SHORTLISTED = "AUTO_SHORTLISTED";
    public static final String TYPE_PLACEMENT = "PLACEMENT_RECORDED";

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
                        "New applicant for " + e.jobTitle() + (e.wasAutoShortlisted() ? " (auto-shortlisted)" : ""),
                        name(e.studentName()) + " applied to " + e.jobTitle()
                                + (e.wasAutoShortlisted() ? " and met the auto-shortlist criteria." : "."),
                        appId, jobId, now));
    }

    public static List<Notification> forStatusChanged(ApplicationEvents.ApplicationStatusChanged e, Instant now) {
        String eventId = e.eventId().toString();
        String appId = e.applicationId().toString();
        String jobId = e.jobId().toString();
        if ("WITHDRAWN".equals(e.newStatus()) && e.bySystem()) {
            String title = name(e.studentName()) + " was withdrawn from " + e.jobTitle() + " by placement policy";
            return List.of(new Notification(eventId, e.recruiterUserId().toString(), TYPE_WITHDRAWN, title,
                    title + (e.reason() == null ? "." : ": " + e.reason() + "."), appId, jobId, now));
        }
        if ("SHORTLISTED".equals(e.newStatus()) && e.bySystem()) {
            String title = "You were automatically shortlisted for " + e.jobTitle() + " at " + e.companyName();
            return List.of(new Notification(eventId, e.studentUserId().toString(), TYPE_AUTO_SHORTLISTED, title,
                    title + ".", appId, jobId, now));
        }
        if ("WITHDRAWN".equals(e.newStatus())) {
            String title = name(e.studentName()) + " withdrew from " + e.jobTitle();
            return List.of(new Notification(eventId, e.recruiterUserId().toString(), TYPE_WITHDRAWN, title,
                    title + ".", appId, jobId, now));
        }
        String title = "Your application for " + e.jobTitle() + " is now " + e.newStatus();
        return List.of(new Notification(eventId, e.studentUserId().toString(), TYPE_STATUS_CHANGED, title,
                title + ".", appId, jobId, now));
    }

    public static List<Notification> forPlacement(ApplicationEvents.StudentPlacementRecorded e, Instant now) {
        String ctc = e.ctcLpa() == null ? "" : " at " + e.ctcLpa().setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString() + " LPA";
        String title = "Placement recorded: " + e.companyName() + ctc;
        String message = title + (e.locked()
                ? ". You are now locked under the placement policy; your other open applications were withdrawn."
                : ". Jobs you can apply to next depend on the placement policy; see your placement status.");
        return List.of(new Notification(e.eventId().toString(), e.studentUserId().toString(), TYPE_PLACEMENT, title,
                message, e.applicationId() == null ? null : e.applicationId().toString(), null, now));
    }

    private static String name(String studentName) {
        return studentName == null || studentName.isBlank() ? "A student" : studentName;
    }
}
