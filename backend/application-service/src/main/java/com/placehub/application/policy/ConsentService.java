package com.placehub.application.policy;

import com.placehub.application.policy.PolicyDtos.ConsentDto;
import com.placehub.application.policy.PolicyRepositories.ConsentRepository;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin-only consent queries and CSV export (spec 20.3). */
@Service
public class ConsentService {

    static final int EXPORT_LIMIT = 10_000;
    private static final Map<String, String> SORTABLE = Map.of("acceptedAt", "acceptedAt");

    private final ConsentRepository consents;

    public ConsentService(ConsentRepository consents) {
        this.consents = consents;
    }

    @Transactional(readOnly = true)
    public PageResponse<ConsentDto> list(UUID jobId, UUID studentId, Integer page, Integer size, String sort) {
        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "acceptedAt"));
        return PageResponse.of(consents.findAll(filter(jobId, studentId), pageable), ConsentService::toDto);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID jobId, UUID studentId) {
        List<Consent> rows = consents.findAll(filter(jobId, studentId),
                org.springframework.data.domain.PageRequest.of(0, EXPORT_LIMIT,
                        Sort.by(Sort.Direction.DESC, "acceptedAt"))).getContent();
        StringBuilder sb = new StringBuilder("consent_id,application_id,student_user_id,job_id,policy_version,band,"
                + "signed_name,accepted_at,client_ip,user_agent,consent_text_hash\r\n");
        for (Consent c : rows) {
            sb.append(String.join(",", csv(c.getId()), csv(c.getApplicationId()), csv(c.getStudentUserId()),
                    csv(c.getJobId()), csv(c.getPolicyVersion()), csv(c.getBand()), csv(c.getSignedName()),
                    csv(c.getAcceptedAt()), csv(c.getClientIp()), csv(c.getUserAgent()),
                    csv(c.getConsentTextHash()))).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * RFC 4180 quoting plus formula-injection protection: values starting with = + - @ tab or CR are prefixed with
     * a single quote so spreadsheet tools treat them as text.
     */
    static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static Specification<Consent> filter(UUID jobId, UUID studentId) {
        Specification<Consent> spec = (root, q, cb) -> cb.conjunction();
        if (jobId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("jobId"), jobId));
        }
        if (studentId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("studentUserId"), studentId));
        }
        return spec;
    }

    static ConsentDto toDto(Consent c) {
        return new ConsentDto(c.getId(), c.getApplicationId(), c.getStudentUserId(), c.getJobId(),
                c.getPolicyVersion(), c.getBand(), c.getConsentTextHash(), c.getRenderedText(), c.getSignedName(),
                c.getAcceptedAt(), c.getClientIp(), c.getUserAgent());
    }
}
