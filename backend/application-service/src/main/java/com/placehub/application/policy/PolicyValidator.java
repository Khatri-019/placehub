package com.placehub.application.policy;

import com.placehub.application.policy.PolicyModel.Action;
import com.placehub.application.policy.PolicyModel.Band;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Pure validation of a proposed policy (spec 20.3 band rules). Returns every problem; empty means valid. */
public final class PolicyValidator {

    public static final BigDecimal MAX_MULTIPLIER = new BigDecimal("100");
    public static final int MAX_CONSENT_TEXT = 20_000;
    public static final int MAX_CHANGE_NOTE = 500;

    private PolicyValidator() {
    }

    public static List<String> validateBands(List<Band> bands) {
        List<String> errors = new ArrayList<>();
        if (bands == null || bands.isEmpty()) {
            errors.add("At least one band is required");
            return errors;
        }
        for (int i = 0; i < bands.size(); i++) {
            Band b = bands.get(i);
            String where = "Band " + (i + 1);
            if (b == null || b.fromMultiplier() == null || b.action() == null) {
                errors.add(where + ": from multiplier and action are required");
                continue;
            }
            if (i == 0 && b.fromMultiplier().signum() != 0) {
                errors.add(where + ": the first band must start at 0");
            }
            if (i > 0) {
                checkMultiplier(errors, where + " from", b.fromMultiplier());
            }
            boolean last = i == bands.size() - 1;
            if (b.toMultiplier() == null) {
                if (!last) {
                    errors.add(where + ": only the last band may be open-ended");
                }
            } else {
                checkMultiplier(errors, where + " to", b.toMultiplier());
                if (b.toMultiplier().compareTo(b.fromMultiplier()) <= 0) {
                    errors.add(where + ": 'to' (" + b.toMultiplier().toPlainString() + ") must be greater than 'from' ("
                            + b.fromMultiplier().toPlainString() + ")");
                }
                if (last) {
                    errors.add(where + ": the last band must be open-ended (no 'to' value)");
                }
            }
            if (!last && bands.get(i + 1) != null && b.toMultiplier() != null
                    && bands.get(i + 1).fromMultiplier() != null) {
                int cmp = b.toMultiplier().compareTo(bands.get(i + 1).fromMultiplier());
                if (cmp < 0) {
                    errors.add("Gap between band " + (i + 1) + " and band " + (i + 2) + ": "
                            + b.toMultiplier().toPlainString() + " to " + bands.get(i + 1).fromMultiplier().toPlainString()
                            + " is not covered");
                } else if (cmp > 0) {
                    errors.add("Band " + (i + 1) + " and band " + (i + 2) + " overlap");
                }
            }
        }
        return errors;
    }

    public static List<String> validate(List<Band> bands, Integer maxActiveConsents, String consentText,
                                        String changeNote) {
        List<String> errors = validateBands(bands);
        boolean needsConsent = bands != null && bands.stream().anyMatch(b -> b != null && b.action() == Action.REQUIRE_CONSENT);
        if (needsConsent && (consentText == null || consentText.isBlank())) {
            errors.add("Consent text is required when a band requires consent");
        }
        if (consentText != null && consentText.length() > MAX_CONSENT_TEXT) {
            errors.add("Consent text must be at most " + MAX_CONSENT_TEXT + " characters");
        }
        if (maxActiveConsents != null && maxActiveConsents < 1) {
            errors.add("Max active consents must be empty or at least 1");
        }
        if (changeNote != null && changeNote.length() > MAX_CHANGE_NOTE) {
            errors.add("Change note must be at most " + MAX_CHANGE_NOTE + " characters");
        }
        return errors;
    }

    private static void checkMultiplier(List<String> errors, String what, BigDecimal value) {
        if (value.signum() <= 0) {
            errors.add(what + " must be greater than 0");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            errors.add(what + " must have at most 2 decimals");
        }
        if (value.compareTo(MAX_MULTIPLIER) > 0) {
            errors.add(what + " must be at most " + MAX_MULTIPLIER.toPlainString());
        }
    }
}
