package com.placehub.notification.email;

/** Outbound email port. v1 ships only {@link ConsoleEmailSender}; an SMTP adapter can be swapped in later. */
public interface EmailSender {

    void send(String recipientUserId, String subject, String body);
}
