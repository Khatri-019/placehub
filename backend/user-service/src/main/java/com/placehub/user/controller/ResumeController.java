package com.placehub.user.controller;

import com.placehub.common.error.ApiException;
import com.placehub.common.security.AuthUser;
import com.placehub.common.web.PdfDownloads;
import com.placehub.user.dto.ResumeDtos.ResumeMetadata;
import com.placehub.user.dto.ResumeDtos.ResumeOverview;
import com.placehub.user.service.ResumeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/** Student resume management (spec 20.2). Only the authenticated student's own resumes are ever touched. */
@RestController
@RequestMapping("/api/users/me/resume")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "resumes")
public class ResumeController {

    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeMetadata> upload(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestPart("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
        ResumeMetadata saved = resumes.upload(AuthUser.from(jwt).id(), file.getOriginalFilename(),
                file.getContentType(), content);
        return ResponseEntity.created(URI.create("/api/users/me/resume")).body(saved);
    }

    @GetMapping
    public ResumeOverview current(@AuthenticationPrincipal Jwt jwt) {
        return resumes.overview(AuthUser.from(jwt).id());
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt) {
        ResumeService.StoredFile f = resumes.currentFile(AuthUser.from(jwt).id());
        return PdfDownloads.attachment(f.content(), f.filename());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> downloadVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ResumeService.StoredFile f = resumes.file(AuthUser.from(jwt).id(), id);
        return PdfDownloads.attachment(f.content(), f.filename());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        resumes.delete(AuthUser.from(jwt).id(), id);
        return ResponseEntity.noContent().build();
    }
}
