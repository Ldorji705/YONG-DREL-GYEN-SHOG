package com.yongdrel.auditlogservice.application.service;

import com.yongdrel.auditlogservice.application.exception.ForbiddenOperationException;
import com.yongdrel.auditlogservice.application.exception.ResourceNotFoundException;
import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.application.model.PageResult;
import com.yongdrel.auditlogservice.application.port.out.AuditEventRepositoryPort;
import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.Caller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.yongdrel.auditlogservice.TestData.ADMIN;
import static com.yongdrel.auditlogservice.TestData.CORRELATION;
import static com.yongdrel.auditlogservice.TestData.NOW;
import static com.yongdrel.auditlogservice.TestData.ORGANIZER;
import static com.yongdrel.auditlogservice.TestData.ORGANIZER_WITHOUT_TENANT;
import static com.yongdrel.auditlogservice.TestData.OTHER_ORGANIZER;
import static com.yongdrel.auditlogservice.TestData.OTHER_STUDENT;
import static com.yongdrel.auditlogservice.TestData.SERVICE;
import static com.yongdrel.auditlogservice.TestData.STUDENT;
import static com.yongdrel.auditlogservice.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock AuditEventRepositoryPort events;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryService(events);
    }

    private AuditSearchCriteria searched(Caller caller, AuditSearchCriteria criteria) {
        when(events.search(any(), anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        service.search(criteria, 0, 20, caller);
        ArgumentCaptor<AuditSearchCriteria> captor = ArgumentCaptor.forClass(AuditSearchCriteria.class);
        verify(events).search(captor.capture(), eq(0), eq(20));
        return captor.getValue();
    }

    @Test
    void adminSearchIsUnrestricted() {
        assertThat(searched(ADMIN, AuditSearchCriteria.none())).isEqualTo(AuditSearchCriteria.none());
    }

    @Test
    void organizerIsForcedToOwnTenant() {
        assertThat(searched(ORGANIZER, AuditSearchCriteria.none()).tenantId()).isEqualTo(10L);
    }

    @Test
    void organizerCannotAskForAnotherTenant() {
        assertThatThrownBy(() -> service.search(AuditSearchCriteria.none().withTenantId(20L), 0, 20, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(events);
    }

    @Test
    void organizerWithoutTenantClaimIsRejected() {
        assertThatThrownBy(() -> service.search(AuditSearchCriteria.none(), 0, 20, ORGANIZER_WITHOUT_TENANT))
                .isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("tenantId");
    }

    @Test
    void studentSeesOnlyOwnActivity() {
        assertThat(searched(STUDENT, AuditSearchCriteria.none()).actorId()).isEqualTo(100L);
    }

    @Test
    void studentCannotAskForAnotherActor() {
        assertThatThrownBy(() -> service.search(AuditSearchCriteria.none().withActorId(999L), 0, 20, STUDENT))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void servicesCannotRead() {
        assertThatThrownBy(() -> service.search(AuditSearchCriteria.none(), 0, 20, SERVICE))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.get(1L, SERVICE)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void invalidRangesAreRejected() {
        var reversed = new AuditSearchCriteria(null, null, null, null, null, null, null, null, null, NOW, NOW.minusSeconds(1));
        var tooLong = new AuditSearchCriteria(null, null, null, null, null, null, null, null, null,
                NOW.minus(Duration.ofDays(367)), NOW);
        assertThatThrownBy(() -> service.search(reversed, 0, 20, ADMIN)).isInstanceOf(InvalidAuditEventException.class);
        assertThatThrownBy(() -> service.search(tooLong, 0, 20, ADMIN)).hasMessageContaining("366 days");
    }

    @Test
    void validRangeIsPassedThrough() {
        var range = new AuditSearchCriteria(null, null, null, null, null, null, null, null, null,
                NOW.minus(Duration.ofDays(366)), NOW);
        assertThat(searched(ADMIN, range).from()).isEqualTo(range.from());
    }

    @Test
    void getAppliesVisibility() {
        when(events.findById(1L)).thenReturn(Optional.of(event(1L)));

        assertThat(service.get(1L, ADMIN).id()).isEqualTo(1L);
        assertThat(service.get(1L, ORGANIZER).id()).isEqualTo(1L);
        assertThat(service.get(1L, STUDENT).id()).isEqualTo(1L);
        assertThatThrownBy(() -> service.get(1L, OTHER_ORGANIZER)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.get(1L, OTHER_STUDENT)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUnknownIs404() {
        when(events.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void timelineIsScopedAndOrdered() {
        when(events.timeline(any(), eq(AuditQueryService.TIMELINE_LIMIT))).thenReturn(List.of(event(1L)));

        List<AuditEvent> result = service.timeline(CORRELATION, ORGANIZER);

        assertThat(result).hasSize(1);
        ArgumentCaptor<AuditSearchCriteria> captor = ArgumentCaptor.forClass(AuditSearchCriteria.class);
        verify(events).timeline(captor.capture(), eq(500));
        assertThat(captor.getValue().correlationId()).isEqualTo(CORRELATION);
        assertThat(captor.getValue().tenantId()).isEqualTo(10L);
    }

    @Test
    void emptyTimelineIs404() {
        when(events.timeline(any(), anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> service.timeline(CORRELATION, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void malformedCorrelationIdIs400() {
        assertThatThrownBy(() -> service.timeline("bad id", ADMIN)).isInstanceOf(InvalidAuditEventException.class);
        assertThatThrownBy(() -> service.timeline(null, ADMIN)).isInstanceOf(InvalidAuditEventException.class);
    }
}
