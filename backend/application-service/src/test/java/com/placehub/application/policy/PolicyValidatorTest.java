package com.placehub.application.policy;

import com.placehub.application.policy.PolicyModel.Action;
import com.placehub.application.policy.PolicyModel.Band;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyValidatorTest {

    static Band b(String from, String to, Action action) {
        return new Band(new BigDecimal(from), to == null ? null : new BigDecimal(to), action, false);
    }

    @Test
    void defaultPolicyIsValid() {
        assertThat(PolicyValidator.validate(List.of(b("0", "1.5", Action.BLOCK), b("1.5", "2.0", Action.REQUIRE_CONSENT),
                b("2.0", null, Action.ALLOW)), null, "Consent text", "note")).isEmpty();
        assertThat(PolicyValidator.validateBands(List.of(b("0", null, Action.ALLOW)))).isEmpty();
    }

    @Test
    void mustStartAtZeroAndEndOpen() {
        assertThat(PolicyValidator.validateBands(List.of(b("0.5", null, Action.ALLOW))))
                .anyMatch(e -> e.contains("must start at 0"));
        assertThat(PolicyValidator.validateBands(List.of(b("0", "1.5", Action.BLOCK), b("1.5", "3", Action.ALLOW))))
                .anyMatch(e -> e.contains("last band must be open-ended"));
        assertThat(PolicyValidator.validateBands(List.of(b("0", null, Action.BLOCK), b("1.5", null, Action.ALLOW))))
                .anyMatch(e -> e.contains("only the last band may be open-ended"));
        assertThat(PolicyValidator.validateBands(List.of())).containsExactly("At least one band is required");
    }

    @Test
    void gapsAndOverlapsAreReported() {
        assertThat(PolicyValidator.validateBands(List.of(b("0", "1.5", Action.BLOCK), b("1.6", null, Action.ALLOW))))
                .anyMatch(e -> e.startsWith("Gap between band 1 and band 2"));
        assertThat(PolicyValidator.validateBands(List.of(b("0", "1.5", Action.BLOCK), b("1.4", null, Action.ALLOW))))
                .anyMatch(e -> e.contains("overlap"));
    }

    @Test
    void multipliersArePositiveWithTwoDecimalsAndIncreasing() {
        assertThat(PolicyValidator.validateBands(List.of(b("0", "1.555", Action.BLOCK), b("1.555", null, Action.ALLOW))))
                .anyMatch(e -> e.contains("at most 2 decimals"));
        assertThat(PolicyValidator.validateBands(List.of(b("0", "2", Action.BLOCK), b("2", "1.5", Action.ALLOW),
                b("1.5", null, Action.ALLOW)))).anyMatch(e -> e.contains("must be greater than 'from'"));
        assertThat(PolicyValidator.validateBands(List.of(b("0", "0", Action.BLOCK), b("0", null, Action.ALLOW))))
                .anyMatch(e -> e.contains("greater than 0"));
        assertThat(PolicyValidator.validateBands(List.of(b("0", "101", Action.BLOCK), b("101", null, Action.ALLOW))))
                .anyMatch(e -> e.contains("at most 100"));
        // Trailing zeros are fine: 1.50 has two decimals.
        assertThat(PolicyValidator.validateBands(List.of(b("0", "1.50", Action.BLOCK), b("1.50", null, Action.ALLOW))))
                .isEmpty();
    }

    @Test
    void consentTextRequiredForConsentBandsAndLimitsChecked() {
        List<Band> bands = List.of(b("0", "1.5", Action.REQUIRE_CONSENT), b("1.5", null, Action.ALLOW));
        assertThat(PolicyValidator.validate(bands, null, " ", "n"))
                .contains("Consent text is required when a band requires consent");
        assertThat(PolicyValidator.validate(bands, 0, "x", "n")).anyMatch(e -> e.contains("Max active consents"));
        assertThat(PolicyValidator.validate(bands, null, "x".repeat(20_001), "n")).anyMatch(e -> e.contains("Consent text must"));
        assertThat(PolicyValidator.validate(bands, null, "x", "n".repeat(501))).anyMatch(e -> e.contains("Change note"));
        assertThat(PolicyValidator.validate(bands, 3, "x", "n")).isEmpty();
    }
}
