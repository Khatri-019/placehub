package com.placehub.common.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Download headers for PDF files (spec 20.2): attachment, nosniff, private no-store. */
public final class PdfDownloads {

    private PdfDownloads() {
    }

    public static ResponseEntity<byte[]> attachment(byte[] content, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename == null || filename.isBlank() ? "resume.pdf" : filename, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }

    /** Extracts the filename from an upstream Content-Disposition header, if any. */
    public static String filenameOf(HttpHeaders headers) {
        ContentDisposition cd = headers.getContentDisposition();
        return cd.getFilename();
    }
}
