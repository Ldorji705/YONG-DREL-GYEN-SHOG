package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.model.FlagSearchCriteria;
import com.yongdrel.fraudriskservice.application.model.PageResult;
import com.yongdrel.fraudriskservice.application.port.out.FlagEventPublisherPort;
import com.yongdrel.fraudriskservice.application.port.out.RiskFlagRepositoryPort;
import com.yongdrel.fraudriskservice.domain.exception.InvalidFlagTransitionException;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.yongdrel.fraudriskservice.TestData.ADMIN;
import static com.yongdrel.fraudriskservice.TestData.NOW;
import static com.yongdrel.fraudriskservice.TestData.ORGANIZER;
import static com.yongdrel.fraudriskservice.TestData.ORGANIZER_WITHOUT_TENANT;
import static com.yongdrel.fraudriskservice.TestData.OTHER_ORGANIZER;
import static com.yongdrel.fraudriskservice.TestData.OTHER_TENANT;
import static com.yongdrel.fraudriskservice.TestData.SERVICE;
import static com.yongdrel.fraudriskservice.TestData.STUDENT;
import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.openFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagReviewServiceTest {

    @Mock RiskFlagRepositoryPort flags;
    @Mock FlagEventPublisherPort publisher;

    private FlagReviewService service;

    private final FlagSearchCriteria openOnly = new FlagSearchCriteria(null, null, null, null, FlagStatus.OPEN, null);

    @BeforeEach
    void setUp() {
        service = new FlagReviewService(flags, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void organizerSearchIsForcedToOwnTenant() {
        when(flags.search(any(), eq(0), eq(20))).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        service.search(openOnly, 0, 20, ORGANIZER);

        ArgumentCaptor<FlagSearchCriteria> captor = ArgumentCaptor.forClass(FlagSearchCriteria.class);
        verify(flags).search(captor.capture(), eq(0), eq(20));
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().status()).isEqualTo(FlagStatus.OPEN);
    }

    @Test
    void organizerMayPassOwnTenantExplicitly() {
        when(flags.search(any(), eq(0), eq(20))).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        service.search(openOnly.withTenantId(TENANT), 0, 20, ORGANIZER);
        verify(flags).search(openOnly.withTenantId(TENANT), 0, 20);
    }

    @Test
    void organizerCannotSearchAnotherTenant() {
        assertThatThrownBy(() -> service.search(openOnly.withTenantId(OTHER_TENANT), 0, 20, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(flags);
    }

    @Test
    void organizerWithoutTenantClaimCannotSearch() {
        assertThatThrownBy(() -> service.search(openOnly, 0, 20, ORGANIZER_WITHOUT_TENANT))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void adminAndServiceSearchAcrossTenants() {
        when(flags.search(openOnly, 1, 5)).thenReturn(new PageResult<>(List.of(openFlag(1L, TENANT)), 1, 5, 6));

        PageResult<RiskFlag> page = service.search(openOnly, 1, 5, ADMIN);
        service.search(openOnly, 1, 5, SERVICE);

        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    void studentsCannotReadFlags() {
        assertThatThrownBy(() -> service.search(openOnly, 0, 20, STUDENT)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.get(1L, STUDENT)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void getReturnsVisibleFlagAndHidesOthers() {
        when(flags.findById(1L)).thenReturn(Optional.of(openFlag(1L, TENANT)));

        assertThat(service.get(1L, ORGANIZER).getId()).isEqualTo(1L);
        assertThatThrownBy(() -> service.get(1L, OTHER_ORGANIZER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void organizerConfirmsFraudOnOwnFlag() {
        when(flags.findById(1L)).thenReturn(Optional.of(openFlag(1L, TENANT)));
        when(flags.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskFlag reviewed = service.review(1L, FlagStatus.CONFIRMED_FRAUD, "Reference reused", ORGANIZER);

        assertThat(reviewed.getStatus()).isEqualTo(FlagStatus.CONFIRMED_FRAUD);
        assertThat(reviewed.getReviewerId()).isEqualTo(ORGANIZER.userId());
        assertThat(reviewed.getReviewedAt()).isEqualTo(NOW);
        verify(publisher).flagReviewed(reviewed);
    }

    @Test
    void reviewOfAnotherTenantsFlagIs404() {
        when(flags.findById(1L)).thenReturn(Optional.of(openFlag(1L, TENANT)));

        assertThatThrownBy(() -> service.review(1L, FlagStatus.DISMISSED, "x", OTHER_ORGANIZER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(flags, never()).save(any());
    }

    @Test
    void serviceAccountsCannotReview() {
        assertThatThrownBy(() -> service.review(1L, FlagStatus.DISMISSED, "x", SERVICE))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(flags);
    }

    @Test
    void closedFlagCannotBeReopened() {
        RiskFlag closed = openFlag(1L, TENANT);
        closed.review(FlagStatus.DISMISSED, 3L, "ok", NOW);
        when(flags.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.review(1L, FlagStatus.CONFIRMED_FRAUD, "late", ADMIN))
                .isInstanceOf(InvalidFlagTransitionException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void unknownFlagIs404() {
        when(flags.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }
}
