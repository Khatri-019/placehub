package com.placehub.application.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Consent text rendering and signature checks (spec 20.3). Pure. */
public final class ConsentText {

    private ConsentText() {
    }

    public static String render(String template, String companyName, String jobTitle, BigDecimal jobCtc,
                                BigDecimal currentCtc) {
        String t = template == null ? "" : template;
        return t.replace("{{companyName}}", nz(companyName))
                .replace("{{jobTitle}}", nz(jobTitle))
                .replace("{{ctcLpa}}", money(jobCtc))
                .replace("{{currentCtc}}", money(currentCtc));
    }

    /** The signed name must equal the student's full name, ignoring case and surrounding/duplicated whitespace. */
    public static boolean signatureMatches(String signedName, String fullName) {
        return signedName != null && fullName != null && !signedName.isBlank()
                && normalise(signedName).equals(normalise(fullName));
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalise(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
