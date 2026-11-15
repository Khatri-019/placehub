package com.placehub.application.policy;

import com.placehub.application.policy.PolicyModel.PlacementState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentAndSummaryTest {

    @Test
    void consentTextRendersAllPlaceholders() {
        assertThat(ConsentText.render("{{jobTitle}} at {{companyName}}: {{ctcLpa}} vs {{currentCtc}} ({{jobTitle}})",
                "Meridian", "Senior DE", new BigDecimal("20"), new BigDecimal("12.5")))
                .isEqualTo("Senior DE at Meridian: 20.00 vs 12.50 (Senior DE)");
        assertThat(ConsentText.render(null, null, null, null, null)).isEmpty();
    }

    @Test
    void signatureMatchesFullNameIgnoringCaseAndSpacing() {
        assertThat(ConsentText.signatureMatches("  asha   RAO ", "Asha Rao")).isTrue();
        assertThat(ConsentText.signatureMatches("Asha", "Asha Rao")).isFalse();
        assertThat(ConsentText.signatureMatches("", "Asha Rao")).isFalse();
        assertThat(ConsentText.signatureMatches(null, "Asha Rao")).isFalse();
    }

    @Test
    void consentHashIsStableSha256() {
        assertThat(ConsentText.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void csvValuesAreQuotedAndFormulaSafe() {
        assertThat(ConsentService.csv("plain")).isEqualTo("\"plain\"");
        assertThat(ConsentService.csv("a \"quoted\", value")).isEqualTo("\"a \"\"quoted\"\", value\"");
        assertThat(ConsentService.csv("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(ConsentService.csv("+1")).isEqualTo("\"'+1\"");
        assertThat(ConsentService.csv("@cmd")).isEqualTo("\"'@cmd\"");
        assertThat(ConsentService.csv(null)).isEmpty();
    }

    @Test
    void studentBannerDescribesEachBandInPlainWords() {
        var rules = PolicyEvaluatorTest.defaults(false);
        assertThat(PlacementService.summary(new PlacementState(new BigDecimal("12.00"), false), rules))
                .isEqualTo("Placed at 12.00 LPA. You can apply to jobs paying 18.00 LPA or more. "
                        + "18.00 to 23.99 LPA needs your consent and locks you if selected. "
                        + "Internships are not restricted.");
        assertThat(PlacementService.summary(PlacementState.UNPLACED, rules))
                .isEqualTo("You are not placed yet, so no CTC restrictions apply to you.");
        assertThat(PlacementService.summary(new PlacementState(new BigDecimal("20.00"), true), rules))
                .startsWith("You are locked under the placement policy after your placement at 20.00 LPA");
    }
}
