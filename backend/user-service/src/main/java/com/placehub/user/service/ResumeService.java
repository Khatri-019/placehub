package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import com.placehub.user.domain.Role;
import com.placehub.user.dto.ResumeDtos.ResumeMetadata;
import com.placehub.user.dto.ResumeDtos.ResumeOverview;
import com.placehub.user.entity.Resume;
import com.placehub.user.repository.ResumeRepository;
import com.placehub.user.repository.ResumeRepository.ResumeSummary;
import com.placehub.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Resume versions (spec 20.2). Keeps the newest {@link #KEEP_VERSIONS}, plus any version an application references. */
@Service
public class ResumeService {

    public static final int KEEP_VERSIONS = 3;

    private final ResumeRepository resumes;
    private final UserRepository users;
    private final Clock clock;

    public ResumeService(ResumeRepository resumes, UserRepository users, Clock clock) {
        this.resumes = resumes;
        this.users = users;
        this.clock = clock;
    }

    public record StoredFile(String filename, byte[] content) {
    }

    @Transactional
    public ResumeMetadata upload(UUID studentId, String originalFilename, String contentType, byte[] content) {
        users.findById(studentId).filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.forbidden("Only students can upload resumes"));
        ResumeValidator.validate(content, contentType);
        resumes.clearCurrent(studentId);
        resumes.flush();
        Resume saved = resumes.saveAndFlush(new Resume(UUID.randomUUID(), studentId,
                ResumeValidator.sanitizeFilename(originalFilename), content, sha256(content), clock.instant()));
        prune(studentId);
        return new ResumeMetadata(saved.getId(), saved.getOriginalFilename(), saved.getSizeBytes(), saved.getSha256(),
                saved.getUploadedAt(), true, false);
    }

    @Transactional(readOnly = true)
    public ResumeOverview overview(UUID studentId) {
        List<ResumeMetadata> versions = resumes.findByUserIdOrderByUploadedAtDesc(studentId).stream()
                .map(ResumeService::toMetadata).toList();
        ResumeMetadata current = versions.stream().filter(ResumeMetadata::current).findFirst()
                .orElseThrow(() -> ApiException.notFound("No resume uploaded yet"));
        return new ResumeOverview(current, versions);
    }

    @Transactional(readOnly = true)
    public StoredFile currentFile(UUID studentId) {
        UUID currentId = resumes.findFirstByUserIdAndCurrentTrue(studentId).map(ResumeSummary::getId)
                .orElseThrow(() -> ApiException.notFound("No resume uploaded yet"));
        return file(studentId, currentId);
    }

    /** Owner-scoped lookup: a resume id belonging to someone else is reported as not found. */
    @Transactional(readOnly = true)
    public StoredFile file(UUID userId, UUID resumeId) {
        Resume r = resumes.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume not found"));
        return new StoredFile(r.getOriginalFilename(), r.getContent());
    }

    @Transactional
    public void delete(UUID studentId, UUID resumeId) {
        Resume r = resumes.findByIdAndUserId(resumeId, studentId)
                .orElseThrow(() -> ApiException.notFound("Resume not found"));
        if (r.isReferenced()) {
            throw ApiException.conflict("This resume version was sent with an application and cannot be deleted");
        }
        boolean wasCurrent = r.isCurrent();
        resumes.delete(r);
        resumes.flush();
        if (wasCurrent) {
            // Promote the newest remaining version so the student still has a current resume.
            resumes.findByUserIdOrderByUploadedAtDesc(studentId).stream().findFirst()
                    .flatMap(next -> resumes.findById(next.getId()))
                    .ifPresent(next -> {
                        next.markCurrent();
                        resumes.save(next);
                    });
        }
    }

    /** Called by Application Service when an application captures this version. */
    @Transactional
    public void markReferenced(UUID userId, UUID resumeId) {
        if (resumes.markReferenced(resumeId, userId) == 0) {
            throw ApiException.notFound("Resume not found");
        }
    }

    @Transactional(readOnly = true)
    public UUID currentResumeId(UUID userId) {
        return resumes.findFirstByUserIdAndCurrentTrue(userId).map(ResumeSummary::getId).orElse(null);
    }

    private void prune(UUID studentId) {
        List<ResumeSummary> all = resumes.findByUserIdOrderByUploadedAtDesc(studentId);
        List<UUID> stale = all.stream().skip(KEEP_VERSIONS)
                .filter(r -> !r.isReferenced() && !r.isCurrent())
                .map(ResumeSummary::getId).toList();
        if (!stale.isEmpty()) {
            resumes.deletePrunable(stale);
        }
    }

    private static ResumeMetadata toMetadata(ResumeSummary r) {
        return new ResumeMetadata(r.getId(), r.getOriginalFilename(), r.getSizeBytes(), r.getSha256(), r.getUploadedAt(),
                r.isCurrent(), r.isReferenced());
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
