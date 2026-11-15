package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-email limit on failed sign-ins (security review F5): after {@code maxFailures} failures within {@code window}
 * every attempt for that email gets 429 until the window ends. Unknown emails are counted the same way, so the
 * throttle does not reveal which accounts exist. In memory: one User Service instance in this deployment.
 */
public class LoginThrottle {

    public static final int DEFAULT_MAX_FAILURES = 10;
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 50_000;

    private record Window(Instant start, int failures) {
    }

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<String, Window> failures = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock, int maxFailures, Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    public void checkAllowed(String email) {
        Window w = current(email);
        if (w != null && w.failures() >= maxFailures) {
            long minutes = Math.max(1, Duration.between(clock.instant(), w.start().plus(window)).toMinutes() + 1);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-in attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
    }

    public void recordFailure(String email) {
        if (failures.size() >= MAX_TRACKED) {
            failures.clear();
        }
        failures.compute(email, (k, w) -> {
            Instant now = clock.instant();
            if (w == null || !now.isBefore(w.start().plus(window))) {
                return new Window(now, 1);
            }
            return new Window(w.start(), w.failures() + 1);
        });
    }

    public void recordSuccess(String email) {
        failures.remove(email);
    }

    private Window current(String email) {
        Window w = failures.get(email);
        if (w != null && !clock.instant().isBefore(w.start().plus(window))) {
            failures.remove(email, w);
            return null;
        }
        return w;
    }
}
