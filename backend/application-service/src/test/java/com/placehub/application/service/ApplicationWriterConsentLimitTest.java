package com.placehub.application.service;

import com.placehub.application.entity.Application;
import com.placehub.application.messaging.OutboxWriter;
import com.placehub.application.policy.PlacementService;
import com.placehub.application.policy.PolicyRepositories.ConsentRepository;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security review F8: the active-consent limit is re-checked inside the create transaction while holding a per-student
 * lock, so parallel applies cannot both pass the earlier, unlocked check.
 */
class ApplicationWriterConsentLimitTest {

    final ApplicationRepository applications = mock(ApplicationRepository.class);
    final ConsentRepository consents = mock(ConsentRepository.class);
    final ApplicationWriter writer = new ApplicationWriter(applications, mock(OutboxWriter.class),
            mock(StatusHistoryWriter.class), consents, mock(PlacementService.class));
    final UUID studentId = UUID.randomUUID();

    Application application() {
        return new Application(UUID.randomUUID(), UUID.randomUUID(), studentId, UUID.randomUUID(), "SDE", "Meridian",
                "Asha", "asha@example.com", "CSE", new BigDecimal("8.60"), 2027, 0);
    }

    @Test
    void limitReachedUnderTheLockRejectsWithoutWriting() {
        when(applications.countActiveConsentApplications(studentId)).thenReturn(2L);
        assertThatThrownBy(() -> writer.create(application(), appId -> null, false, 2))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(422));
        InOrder order = inOrder(applications);
        order.verify(applications).lockStudentForConsent(studentId.toString());
        order.verify(applications).countActiveConsentApplications(studentId);
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void underTheLimitOrWithoutConsentWritesNormally() {
        when(applications.countActiveConsentApplications(studentId)).thenReturn(1L);
        when(applications.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        writer.create(application(), appId -> null, false, 2);
        verify(applications).saveAndFlush(any());
        writer.create(application(), null, false, 2);
        verify(applications, org.mockito.Mockito.times(1)).lockStudentForConsent(any());
    }
}
