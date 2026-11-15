package com.placehub.application.policy;

import com.placehub.application.client.ClientDtos;
import com.placehub.application.client.DependencyGateway;
import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.messaging.OutboxWriter;
import com.placehub.application.policy.PolicyDtos.BandExplanation;
import com.placehub.application.policy.PolicyDtos.OffCampusRequest;
import com.placehub.application.policy.PolicyDtos.PlacementDto;
import com.placehub.application.policy.PolicyDtos.PlacementStateView;
import com.placehub.application.policy.PolicyDtos.StudentLockDto;
import com.placehub.application.policy.PolicyModel.Action;
import com.placehub.application.policy.PolicyModel.Band;
import com.placehub.application.policy.PolicyModel.PlacementState;
import com.placehub.application.policy.PolicyModel.Rules;
import com.placehub.application.policy.PolicyRepositories.PlacementRepository;
import com.placehub.application.policy.PolicyRepositories.StudentPlacementStateRepository;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.application.service.StatusHistoryWriter;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Placements, student locks and auto-withdrawal (spec 20.3). */
@Service
public class PlacementService {

    public static final String WITHDRAW_REASON = "Withdrawn by placement policy: student placed";
    private static final Map<String, String> SORTABLE = Map.of("recordedAt", "recordedAt", "ctcLpa", "ctcLpa",
            "companyName", "companyName", "studentName", "studentName");

    private final PlacementRepository placements;
    private final StudentPlacementStateRepository states;
    private final ApplicationRepository applications;
    private final OutboxWriter outbox;
    private final StatusHistoryWriter history;
    private final PolicyService policies;
    private final DependencyGateway dependencies;
    private final Clock clock;

    public PlacementService(PlacementRepository placements, StudentPlacementStateRepository states,
                            ApplicationRepository applications, OutboxWriter outbox, StatusHistoryWriter history,
                            PolicyService policies, DependencyGateway dependencies, Clock clock) {
        this.placements = placements;
        this.states = states;
        this.applications = applications;
        this.outbox = outbox;
        this.history = history;
        this.policies = policies;
        this.dependencies = dependencies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlacementState state(UUID studentId) {
        BigDecimal current = placements.highestCtc(studentId);
        boolean locked = states.findById(studentId).map(StudentPlacementState::isLocked).orElse(false);
        return new PlacementState(current, locked);
    }

    /**
     * Records an on-campus placement inside the selection transaction. Locks the student when the application's band
     * snapshot locks, and then (per the active policy) withdraws their other open applications as SYSTEM.
     *
     * @return how many other applications were withdrawn
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int recordOnCampus(Application selected, UUID actorId) {
        if (!"FULL_TIME".equals(selected.getEmploymentTypeSnapshot()) || placements.existsByApplicationId(selected.getId())) {
            return 0;
        }
        boolean lock = selected.isLockOnPlacementSnapshot();
        Placement placement = placements.save(new Placement(UUID.randomUUID(), selected.getStudentUserId(),
                selected.getStudentNameSnapshot(), selected.getId(), selected.getCompanyNameSnapshot(),
                selected.getCtcLpaSnapshot(), selected.getPolicyBandSnapshot(), lock, Placement.Source.ON_CAMPUS,
                clock.instant(), actorId));
        int withdrawn = 0;
        if (lock) {
            withdrawn = lockInternal(selected.getStudentUserId(), "Placed at " + selected.getCompanyNameSnapshot()
                    + " in a lock band", actorId, selected.getId());
        }
        outbox.placementRecorded(placement);
        return withdrawn;
    }

    @Transactional
    public PlacementDto recordOffCampus(UUID adminId, OffCampusRequest req) {
        ClientDtos.AcademicProfile profile = dependencies.academicProfile(req.studentUserId());
        Placement placement = placements.save(new Placement(UUID.randomUUID(), req.studentUserId(), profile.fullName(),
                null, req.companyName().trim(), req.ctcLpa().setScale(2, RoundingMode.HALF_UP), null, req.lock(),
                Placement.Source.OFF_CAMPUS, clock.instant(), adminId));
        if (req.lock()) {
            lockInternal(req.studentUserId(), "Off-campus placement at " + placement.getCompanyName(), adminId, null);
        }
        outbox.placementRecorded(placement);
        PlacementDto dto = toDto(placement);
        policies.recordAudit(adminId, PolicyAuditEntry.Action.OFF_CAMPUS_RECORDED, req.studentUserId(), null, dto,
                "Off-campus offer recorded");
        return dto;
    }

    @Transactional
    public StudentLockDto lock(UUID adminId, UUID studentId, String reason) {
        dependencies.academicProfile(studentId); // 404 unless this is a student
        boolean before = state(studentId).locked();
        int withdrawn = lockInternal(studentId, reason.trim(), adminId, null);
        StudentPlacementState s = states.findById(studentId).orElseThrow();
        policies.recordAudit(adminId, PolicyAuditEntry.Action.STUDENT_LOCKED, studentId, Map.of("locked", before),
                Map.of("locked", true, "withdrawnApplications", withdrawn), reason.trim());
        return new StudentLockDto(studentId, true, s.getLockReason(), s.getLockedAt(), withdrawn);
    }

    @Transactional
    public StudentLockDto unlock(UUID adminId, UUID studentId, String reason) {
        dependencies.academicProfile(studentId);
        StudentPlacementState s = states.findById(studentId).orElseGet(() -> new StudentPlacementState(studentId,
                clock.instant()));
        boolean before = s.isLocked();
        s.unlock(adminId, clock.instant());
        states.save(s);
        policies.recordAudit(adminId, PolicyAuditEntry.Action.STUDENT_UNLOCKED, studentId, Map.of("locked", before),
                Map.of("locked", false), reason.trim());
        return new StudentLockDto(studentId, false, null, null, 0);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlacementDto> list(Boolean locked, Placement.Source source, String company, Integer page,
                                           Integer size, String sort) {
        Specification<Placement> spec = (root, q, cb) -> cb.conjunction();
        if (locked != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("locked"), locked));
        }
        if (source != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("source"), source));
        }
        if (company != null && !company.isBlank()) {
            String like = "%" + company.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("companyName")), like, '\\'));
        }
        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "recordedAt"));
        return PageResponse.of(placements.findAll(spec, pageable), this::toDto);
    }

    @Transactional(readOnly = true)
    public PlacementStateView view(UUID studentId) {
        PlacementState state = state(studentId);
        Rules rules = policies.activeRules();
        String lockReason = states.findById(studentId).filter(StudentPlacementState::isLocked)
                .map(StudentPlacementState::getLockReason).orElse(null);
        List<PlacementDto> mine = placements.findByStudentUserIdOrderByRecordedAtDesc(studentId).stream()
                .map(this::toDto).toList();
        List<BandExplanation> bands = new ArrayList<>();
        if (state.placed()) {
            for (Band b : rules.bands()) {
                BigDecimal from = state.currentCtc().multiply(b.fromMultiplier()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal to = b.toMultiplier() == null ? null
                        : state.currentCtc().multiply(b.toMultiplier()).setScale(2, RoundingMode.HALF_UP);
                bands.add(new BandExplanation(from, to, b.action().name(), b.lockOnPlacement(), describe(from, to, b)));
            }
        }
        return new PlacementStateView(state.currentCtc(), state.placed(), state.locked(), lockReason, mine,
                rules.version(), rules.effectiveFrom(), rules.appliesToInternships(), bands,
                summary(state, rules));
    }

    /** Plain-language banner, e.g. "Placed at 12.00 LPA. You can apply to jobs paying 18.00 LPA or more. ..." */
    static String summary(PlacementState state, Rules rules) {
        if (state.locked()) {
            return "You are locked under the placement policy" + (state.placed()
                    ? " after your placement at " + money(state.currentCtc()) + " LPA" : "")
                    + ". You cannot apply to new jobs until the placement office unlocks you.";
        }
        if (!state.placed()) {
            return "You are not placed yet, so no CTC restrictions apply to you.";
        }
        StringBuilder sb = new StringBuilder("Placed at ").append(money(state.currentCtc())).append(" LPA.");
        Band firstOpen = rules.bands().stream().filter(b -> b.action() != Action.BLOCK).findFirst().orElse(null);
        if (firstOpen == null) {
            return sb.append(" The placement policy does not allow further applications.").toString();
        }
        sb.append(" You can apply to jobs paying ")
                .append(money(state.currentCtc().multiply(firstOpen.fromMultiplier()))).append(" LPA or more.");
        for (Band b : rules.bands()) {
            if (b.action() == Action.REQUIRE_CONSENT) {
                BigDecimal from = state.currentCtc().multiply(b.fromMultiplier());
                String range = b.toMultiplier() == null ? money(from) + " LPA or more"
                        : money(from) + " to " + money(state.currentCtc().multiply(b.toMultiplier())
                        .subtract(new BigDecimal("0.01"))) + " LPA";
                sb.append(' ').append(range).append(" needs your consent")
                        .append(b.lockOnPlacement() ? " and locks you if selected." : ".");
            }
        }
        if (!rules.appliesToInternships()) {
            sb.append(" Internships are not restricted.");
        }
        return sb.toString();
    }

    private static String describe(BigDecimal from, BigDecimal to, Band b) {
        String range = to == null ? money(from) + " LPA or more"
                : from.signum() == 0 ? "below " + money(to) + " LPA"
                : money(from) + " to " + money(to.subtract(new BigDecimal("0.01"))) + " LPA";
        return switch (b.action()) {
            case BLOCK -> "Jobs paying " + range + ": you cannot apply.";
            case REQUIRE_CONSENT -> "Jobs paying " + range + ": you can apply with your consent"
                    + (b.lockOnPlacement() ? "; if selected you will be locked." : ".");
            case ALLOW -> "Jobs paying " + range + ": open to you"
                    + (b.lockOnPlacement() ? "; if selected you will be locked." : ".");
        };
    }

    /** Locks the student and, if the active policy says so, withdraws their other non-terminal applications. */
    private int lockInternal(UUID studentId, String reason, UUID actorId, UUID keepApplicationId) {
        StudentPlacementState s = states.findById(studentId).orElseGet(() -> new StudentPlacementState(studentId,
                clock.instant()));
        s.lock(reason, actorId, clock.instant());
        states.save(s);
        if (!policies.activeRules().withdrawOtherApplicationsOnLock()) {
            return 0;
        }
        int withdrawn = 0;
        for (Application other : applications.findByStudentUserId(studentId)) {
            if (other.getId().equals(keepApplicationId) || !other.getStatus().canTransitionTo(ApplicationStatus.WITHDRAWN)) {
                continue;
            }
            ApplicationStatus old = other.getStatus();
            other.changeStatus(ApplicationStatus.WITHDRAWN, null);
            Application saved = applications.save(other);
            history.record(saved, old, ApplicationStatus.WITHDRAWN, StatusHistoryWriter.ActorType.SYSTEM, null,
                    WITHDRAW_REASON);
            outbox.statusChanged(saved, old, null, StatusHistoryWriter.ActorType.SYSTEM.name(), WITHDRAW_REASON);
            withdrawn++;
        }
        return withdrawn;
    }

    private PlacementDto toDto(Placement p) {
        boolean lockedNow = states.findById(p.getStudentUserId()).map(StudentPlacementState::isLocked).orElse(false);
        return new PlacementDto(p.getId(), p.getStudentUserId(), p.getStudentName(), p.getApplicationId(),
                p.getCompanyName(), p.getCtcLpa(), p.getBand(), p.isLocked(), p.getSource().name(), p.getRecordedAt(),
                p.getRecordedBy(), lockedNow);
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
