
package com.yongdrel.claim.application.service;

import com.yongdrel.claim.application.exception.ClaimNotFoundException;
import com.yongdrel.claim.domain.model.ClaimStatus;
import com.yongdrel.claim.domain.model.PrizeClaim;
import com.yongdrel.claim.domain.port.ClaimReferenceValidator;
import com.yongdrel.claim.domain.port.PrizeClaimRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrizeClaimApplicationServiceTest {

    @Mock
    private PrizeClaimRepository claimRepository;

    @Mock
    private ClaimReferenceValidator referenceValidator;

    @InjectMocks
    private PrizeClaimApplicationService service;

    private UUID winnerId;
    private UUID prizeId;
    private UUID claimId;
    private String userId;
    private String idempotencyKey;
    private String accessToken;
    private PrizeClaim submittedClaim;

    @BeforeEach
    void setUp() {
        winnerId = UUID.randomUUID();
        prizeId = UUID.randomUUID();
        claimId = UUID.randomUUID();

        userId = "test-user-123";
        idempotencyKey = "claim-request-001";
        accessToken = "test-access-token";

        submittedClaim = new PrizeClaim(
                claimId,
                winnerId,
                prizeId,
                userId,
                ClaimStatus.SUBMITTED,
                idempotencyKey,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );
    }

    @Test
    void submitClaimSuccessfully() {
        when(claimRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey
        )).thenReturn(Optional.empty());

        when(claimRepository.existsByWinnerId(winnerId))
                .thenReturn(false);

        when(claimRepository.save(any(PrizeClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrizeClaim result = service.submitClaim(
                winnerId,
                prizeId,
                userId,
                idempotencyKey,
                accessToken
        );

        assertNotNull(result);
        assertEquals(ClaimStatus.SUBMITTED, result.status());
        assertEquals(winnerId, result.winnerId());
        assertEquals(prizeId, result.prizeId());
        assertEquals(userId, result.userId());

        verify(referenceValidator).validate(
                winnerId, prizeId, userId, accessToken
        );
        verify(claimRepository).save(any(PrizeClaim.class));
    }

    @Test
    void submitClaimReturnsExistingClaimForSameIdempotencyRequest() {
        when(claimRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey
        )).thenReturn(Optional.of(submittedClaim));

        PrizeClaim result = service.submitClaim(
                winnerId,
                prizeId,
                userId,
                idempotencyKey,
                accessToken
        );

        assertEquals(submittedClaim, result);

        verify(claimRepository, never()).save(any());
        verify(referenceValidator, never()).validate(
                any(), any(), any(), any()
        );
    }

    @Test
    void submitClaimRejectsReusedIdempotencyKeyForDifferentClaim() {
        UUID differentWinnerId = UUID.randomUUID();

        when(claimRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey
        )).thenReturn(Optional.of(submittedClaim));

        assertThrows(
                IllegalStateException.class,
                () -> service.submitClaim(
                        differentWinnerId,
                        prizeId,
                        userId,
                        idempotencyKey,
                        accessToken
                )
        );

        verify(claimRepository, never()).save(any());
    }

    @Test
    void submitClaimRejectsDuplicateWinnerClaim() {
        when(claimRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey
        )).thenReturn(Optional.empty());

        when(claimRepository.existsByWinnerId(winnerId))
                .thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> service.submitClaim(
                        winnerId,
                        prizeId,
                        userId,
                        idempotencyKey,
                        accessToken
                )
        );

        verify(referenceValidator, never()).validate(
                any(), any(), any(), any()
        );
        verify(claimRepository, never()).save(any());
    }

    @Test
    void submitClaimRejectsMissingUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submitClaim(
                        winnerId,
                        prizeId,
                        " ",
                        idempotencyKey,
                        accessToken
                )
        );
    }

    @Test
    void submitClaimRejectsMissingIdempotencyKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submitClaim(
                        winnerId,
                        prizeId,
                        userId,
                        " ",
                        accessToken
                )
        );
    }

    @Test
    void submitClaimRejectsIdempotencyKeyLongerThan100Characters() {
        String longKey = "K".repeat(101);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.submitClaim(
                        winnerId,
                        prizeId,
                        userId,
                        longKey,
                        accessToken
                )
        );
    }

    @Test
    void startReviewChangesStatusToUnderReview() {
        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(submittedClaim));

        when(claimRepository.save(any(PrizeClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrizeClaim result = service.startReview(claimId);

        assertEquals(ClaimStatus.UNDER_REVIEW, result.status());
        assertNotNull(result.reviewedAt());
    }

    @Test
    void approveClaimChangesStatusToApproved() {
        PrizeClaim underReview = new PrizeClaim(
                claimId, winnerId, prizeId, userId,
                ClaimStatus.UNDER_REVIEW, idempotencyKey,
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, 0
        );

        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(underReview));

        when(claimRepository.save(any(PrizeClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrizeClaim result = service.approveClaim(claimId);

        assertEquals(ClaimStatus.APPROVED, result.status());
        assertNotNull(result.approvedAt());
    }

    @Test
    void rejectClaimChangesStatusToRejected() {
        PrizeClaim underReview = new PrizeClaim(
                claimId, winnerId, prizeId, userId,
                ClaimStatus.UNDER_REVIEW, idempotencyKey,
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, 0
        );

        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(underReview));

        when(claimRepository.save(any(PrizeClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrizeClaim result = service.rejectClaim(
                claimId, "Claim information is incomplete"
        );

        assertEquals(ClaimStatus.REJECTED, result.status());
        assertEquals(
                "Claim information is incomplete",
                result.rejectionReason()
        );
        assertNotNull(result.rejectedAt());
    }

    @Test
    void rejectClaimRequiresReason() {
        PrizeClaim underReview = new PrizeClaim(
                claimId, winnerId, prizeId, userId,
                ClaimStatus.UNDER_REVIEW, idempotencyKey,
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, 0
        );

        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(underReview));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.rejectClaim(claimId, " ")
        );

        verify(claimRepository, never()).save(any());
    }

    @Test
    void fulfillClaimChangesStatusToFulfilled() {
        PrizeClaim approved = new PrizeClaim(
                claimId, winnerId, prizeId, userId,
                ClaimStatus.APPROVED, idempotencyKey,
                LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), null, null, null, null, 0
        );

        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(approved));

        when(claimRepository.save(any(PrizeClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrizeClaim result = service.fulfillClaim(
                claimId, "DELIVERY-REF-001"
        );

        assertEquals(ClaimStatus.FULFILLED, result.status());
        assertEquals("DELIVERY-REF-001", result.fulfillmentReference());
        assertNotNull(result.fulfilledAt());
    }

    @Test
    void fulfillClaimRequiresReference() {
        PrizeClaim approved = new PrizeClaim(
                claimId, winnerId, prizeId, userId,
                ClaimStatus.APPROVED, idempotencyKey,
                LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), null, null, null, null, 0
        );

        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(approved));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.fulfillClaim(claimId, " ")
        );

        verify(claimRepository, never()).save(any());
    }

    @Test
    void cannotApproveClaimBeforeReview() {
        when(claimRepository.findById(claimId))
                .thenReturn(Optional.of(submittedClaim));

        assertThrows(
                IllegalStateException.class,
                () -> service.approveClaim(claimId)
        );

        verify(claimRepository, never()).save(any());
    }

    @Test
    void getClaimThrowsExceptionWhenClaimDoesNotExist() {
        when(claimRepository.findById(claimId))
                .thenReturn(Optional.empty());

        assertThrows(
                ClaimNotFoundException.class,
                () -> service.getClaim(claimId)
        );
    }

    @Test
    void getMyClaimsReturnsOnlyRepositoryResults() {
        when(claimRepository.findAllByUserId(userId))
                .thenReturn(List.of(submittedClaim));

        List<PrizeClaim> results = service.getMyClaims(userId);

        assertEquals(1, results.size());
        assertEquals(userId, results.get(0).userId());
    }

    @Test
    void getAllClaimsReturnsRepositoryResults() {
        when(claimRepository.findAll())
                .thenReturn(List.of(submittedClaim));

        List<PrizeClaim> results = service.getAllClaims();

        assertEquals(1, results.size());
        assertEquals(submittedClaim, results.get(0));
    }
}
