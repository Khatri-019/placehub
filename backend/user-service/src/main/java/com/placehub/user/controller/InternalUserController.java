package com.placehub.user.controller;

import com.placehub.user.dto.AcademicProfileResponse;
import com.placehub.user.dto.InternalUserResponse;
import com.placehub.common.web.PdfDownloads;
import com.placehub.user.service.ResumeService;
import com.placehub.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service endpoints; protected by X-Internal-Token and never routed by the gateway. */
@RestController
@RequestMapping("/internal/users")
@Tag(name = "internal")
public class InternalUserController {

    private final UserService userService;
    private final ResumeService resumeService;

    public InternalUserController(UserService userService, ResumeService resumeService) {
        this.userService = userService;
        this.resumeService = resumeService;
    }

    /** Streams a resume version, only if it belongs to {@code userId} (otherwise 404). */
    @GetMapping("/{userId}/resumes/{resumeId}/file")
    public ResponseEntity<byte[]> resumeFile(@PathVariable UUID userId, @PathVariable UUID resumeId) {
        ResumeService.StoredFile f = resumeService.file(userId, resumeId);
        return PdfDownloads.attachment(f.content(), f.filename());
    }

    /** Marks a version as captured by an application, so it can no longer be deleted or pruned. */
    @PostMapping("/{userId}/resumes/{resumeId}/reference")
    public ResponseEntity<Void> referenceResume(@PathVariable UUID userId, @PathVariable UUID resumeId) {
        resumeService.markReferenced(userId, resumeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public InternalUserResponse user(@PathVariable UUID id) {
        return userService.internalUser(id);
    }

    @GetMapping("/{id}/academic-profile")
    public AcademicProfileResponse academicProfile(@PathVariable UUID id) {
        return userService.academicProfile(id);
    }
}
