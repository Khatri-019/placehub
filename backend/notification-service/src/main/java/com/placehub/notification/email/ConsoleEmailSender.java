package com.placehub.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Simulated email: logs instead of sending. No real email provider is used in v1. */
@Component
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    @Override
    public void send(String recipientUserId, String subject, String body) {
        log.info("[SIMULATED EMAIL] to user={} subject=\"{}\" body=\"{}\"", recipientUserId, subject, body);
    }
}
