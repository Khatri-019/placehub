package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeValidatorTest {

    static byte[] pdf(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) ' ');
        byte[] magic = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, b, 0, Math.min(magic.length, size));
        return b;
    }

    static HttpStatus status(Throwable t) {
        return ((ApiException) t).getStatus();
    }

    @Test
    void acceptsPdfMagicBytesUpToExactly2Mb() {
        assertThatCode(() -> ResumeValidator.validate(pdf(ResumeValidator.MAX_BYTES), "application/pdf"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ResumeValidator.validate(pdf(200), null)).doesNotThrowAnyException();
        assertThatCode(() -> ResumeValidator.validate(pdf(200), "application/octet-stream")).doesNotThrowAnyException();
    }

    @Test
    void oversizedIs413() {
        assertThatThrownBy(() -> ResumeValidator.validate(pdf(ResumeValidator.MAX_BYTES + 1), "application/pdf"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void nonPdfContentIs415EvenWithPdfTypeAndName() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        assertThatThrownBy(() -> ResumeValidator.validate(png, "application/pdf"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ResumeValidator.validate(html, "application/pdf"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertThatThrownBy(() -> ResumeValidator.validate("%PD".getBytes(StandardCharsets.US_ASCII), null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void pdfBytesWithWrongDeclaredTypeIs415() {
        assertThatThrownBy(() -> ResumeValidator.validate(pdf(100), "text/html"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void emptyIs400() {
        assertThatThrownBy(() -> ResumeValidator.validate(new byte[0], "application/pdf"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> ResumeValidator.validate(null, "application/pdf"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void filenamesAreSanitised() {
        assertThat(ResumeValidator.sanitizeFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(ResumeValidator.sanitizeFilename("C:\\Users\\asha\\My CV.PDF")).isEqualTo("My CV.pdf");
        for (String hostile : new String[]{"cv\r\nContent-Type: text/html.pdf", "résumé\"<script>.pdf",
                "a\u0000b.pdf", "..\\..\\windows\\system32\\cmd.exe", "%2e%2e%2fsecret.pdf"}) {
            String safe = ResumeValidator.sanitizeFilename(hostile);
            assertThat(safe).matches("[A-Za-z0-9._ ()-]+\\.pdf").doesNotContain("..").doesNotContain("/")
                    .doesNotContain("\\").doesNotContain("\"").doesNotStartWith(".");
        }
        assertThat(ResumeValidator.sanitizeFilename("..\\..\\windows\\system32\\cmd.exe")).isEqualTo("cmd.exe.pdf");
        assertThat(ResumeValidator.sanitizeFilename("....pdf")).isEqualTo("resume.pdf");
        assertThat(ResumeValidator.sanitizeFilename(null)).isEqualTo("resume.pdf");
        assertThat(ResumeValidator.sanitizeFilename("notes.txt")).isEqualTo("notes.txt.pdf");
        assertThat(ResumeValidator.sanitizeFilename("x".repeat(300) + ".pdf")).hasSize(100).endsWith(".pdf");
    }
}
