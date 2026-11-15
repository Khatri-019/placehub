package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/** Pure resume checks (spec 20.2): size, PDF magic bytes, declared type, and filename sanitising. */
public final class ResumeValidator {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_NAME = 100;

    private ResumeValidator() {
    }

    /** Validates the upload; the magic bytes are authoritative, the declared content type is only a sanity check. */
    public static void validate(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Resumes must be 2 MB or smaller");
        }
        if (!isPdf(content)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF resumes are accepted");
        }
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String type = declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!type.equals("application/pdf") && !type.equals("application/octet-stream")
                    && !type.equals("application/x-pdf")) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF resumes are accepted");
            }
        }
    }

    public static boolean isPdf(byte[] content) {
        return content != null && content.length >= PDF_MAGIC.length
                && Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC);
    }

    /**
     * Keeps only the final path segment, strips control and reserved characters, collapses whitespace, caps the length
     * and forces a .pdf extension. Never returns an empty name.
     */
    public static String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFKC);
        name = name.replaceAll("[^A-Za-z0-9._ ()-]", "_").replaceAll("\\s+", " ").trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.replaceAll("^[._ ]+", "").replaceAll("[._ ]+$", "");
        if (name.isEmpty()) {
            name = "resume";
        }
        if (name.length() > MAX_NAME - 4) {
            name = name.substring(0, MAX_NAME - 4);
        }
        return name + ".pdf";
    }
}
