package com.placehub.application.service;

import com.placehub.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every reason an application cannot be submitted, so the student sees them all in one 422 (spec 20.3).
 * The body stays backward compatible: {@code details.reasons} is still the list of messages, and
 * {@code details.violations} adds a machine-readable code per reason.
 */
public final class Violations {

    public enum Code {
        NOT_ELIGIBLE, RESUME_REQUIRED, POLICY_BLOCKED, CONSENT_REQUIRED, STUDENT_LOCKED, CONSENT_LIMIT_REACHED
    }

    public record Violation(Code code, String message) {
    }

    public static final String NOT_ELIGIBLE_MESSAGE = "You are not eligible for this job";
    public static final String RESUME_REQUIRED_MESSAGE = "Upload your resume before applying";

    private final List<Violation> items = new ArrayList<>();
    private final Map<String, Object> extraDetails = new LinkedHashMap<>();

    public Violations add(Code code, String message) {
        items.add(new Violation(code, message));
        return this;
    }

    public Violations addAll(Code code, List<String> messages) {
        if (messages != null) {
            messages.forEach(m -> add(code, m));
        }
        return this;
    }

    public Violations detail(String key, Object value) {
        extraDetails.put(key, value);
        return this;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean has(Code code) {
        return items.stream().anyMatch(v -> v.code() == code);
    }

    public List<Violation> list() {
        return List.copyOf(items);
    }

    public List<String> messages() {
        return items.stream().map(Violation::message).toList();
    }

    /** Throws a single 422 carrying every violation; does nothing when there are none. */
    @SuppressWarnings("deprecation") // UNPROCESSABLE_ENTITY keeps the reason phrase from the spec's error example
    public void throwIfAny() {
        if (items.isEmpty()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reasons", messages());
        details.put("violations", items.stream()
                .map(v -> Map.of("code", v.code().name(), "message", v.message())).toList());
        details.putAll(extraDetails);
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, headline(), details);
    }

    private String headline() {
        if (has(Code.NOT_ELIGIBLE)) {
            return NOT_ELIGIBLE_MESSAGE;
        }
        if (items.size() == 1) {
            return items.getFirst().message();
        }
        return "This application cannot be submitted yet";
    }
}
