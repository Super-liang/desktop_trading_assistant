package com.tradingassistant.audit;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserOperationAuditServiceTest {
    @Mock UserOperationAuditRepository repository;

    @Test
    void portfolioAuditContainsOnlySafeMetadata() {
        var service = new UserOperationAuditService(repository);
        UUID userId = UUID.randomUUID();
        service.record(userId, UserOperationAudit.Action.PORTFOLIO_CREATED,
                UUID.randomUUID(), "SSE:600519", "贵州茅台",
                UserOperationAudit.Result.SUCCESS);

        ArgumentCaptor<UserOperationAudit> captor = ArgumentCaptor.forClass(UserOperationAudit.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getInstrumentId()).isEqualTo("SSE:600519");
        assertThat(UserOperationAudit.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("quantity", "costPrice", "password", "token", "requestBody");
    }

    @Test
    void passwordFailureAuditContainsNoCredentialMetadata() {
        var service = new UserOperationAuditService(repository);
        service.recordFailure(UUID.randomUUID(), UserOperationAudit.Action.PASSWORD_CHANGED);

        ArgumentCaptor<UserOperationAudit> captor = ArgumentCaptor.forClass(UserOperationAudit.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(UserOperationAudit.Result.FAILURE);
        assertThat(captor.getValue().getInstrumentId()).isNull();
        assertThat(captor.getValue().getInstrumentName()).isNull();
    }
}
