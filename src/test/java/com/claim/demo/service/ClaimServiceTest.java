package com.claim.demo.service;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.domain.UserRole;
import com.claim.demo.dto.ClaimDTO;
import com.claim.demo.dto.ClaimCreateRequest;
import com.claim.demo.entity.Claim;
import com.claim.demo.entity.ClaimStatusHistory;
import com.claim.demo.entity.User;
import com.claim.demo.event.ClaimStatusEvent;
import com.claim.demo.exception.ClaimNotFoundException;
import com.claim.demo.exception.IdempotencyKeyConflictException;
import com.claim.demo.exception.InvalidClaimTransitionException;
import com.claim.demo.exception.UserNotFoundException;
import com.claim.demo.exception.UnauthorizedClaimAccessException;
import com.claim.demo.repository.ClaimRepository;
import com.claim.demo.repository.ClaimStatusHistoryRepository;
import com.claim.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ClaimStatusHistoryRepository claimStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClaimStatusEventPublisher claimStatusEventPublisher;

    @Mock
    private ClaimCacheService claimCacheService;

    @InjectMocks
    private ClaimService claimService;

    @Test
    void createsClaimAsDraftAndPreservesBigDecimalAmount() {
        Claim claim = claim(1L);
        claim.setClaimAmount(new BigDecimal("1234.56"));
        when(claimRepository.save(claim)).thenReturn(claim);

        ClaimDTO result = claimService.submitClaim(claim);

        assertEquals(ClaimStatus.DRAFT, result.getClaimStatus());
        assertEquals(new BigDecimal("1234.56"), result.getClaimAmount());
        assertNotNull(result.getClaimDate());
        assertNotNull(result.getLastUpdated());
    }

    @Test
    void recordsHistoryForEveryTransitionInApprovedLifecycle() {
        Claim claim = claim(1L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);
        when(claimStatusHistoryRepository.save(any(ClaimStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        claimService.transitionClaimStatus(1L, ClaimStatus.SUBMITTED, "claimant", "Ready");
        claimService.transitionClaimStatus(1L, ClaimStatus.UNDER_REVIEW, "officer", null);
        claimService.transitionClaimStatus(1L, ClaimStatus.APPROVED, "officer", "Verified");
        ClaimDTO result = claimService.transitionClaimStatus(1L, ClaimStatus.SETTLED, "officer", "Paid");

        assertEquals(ClaimStatus.SETTLED, result.getClaimStatus());
        verify(claimRepository, times(4)).save(claim);
        verify(claimStatusHistoryRepository, times(4)).save(any(ClaimStatusHistory.class));
    }

    @Test
    void recordsPreviousAndNewStatusWithActorAndReason() {
        Claim claim = claim(7L);
        when(claimRepository.findById(7L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);

        claimService.transitionClaimStatus(7L, ClaimStatus.SUBMITTED, "claimant@example.com", "Complete");

        ArgumentCaptor<ClaimStatusHistory> historyCaptor = ArgumentCaptor.forClass(ClaimStatusHistory.class);
        verify(claimStatusHistoryRepository).save(historyCaptor.capture());
        ClaimStatusHistory history = historyCaptor.getValue();
        assertEquals(7L, history.getClaimId());
        assertEquals(ClaimStatus.DRAFT, history.getPreviousStatus());
        assertEquals(ClaimStatus.SUBMITTED, history.getNewStatus());
        assertEquals("claimant@example.com", history.getChangedBy());
        assertEquals("Complete", history.getReason());
        assertNotNull(history.getChangedAt());

        ArgumentCaptor<ClaimStatusEvent> eventCaptor = ArgumentCaptor.forClass(ClaimStatusEvent.class);
        verify(claimStatusEventPublisher).publishAfterCommit(eventCaptor.capture());
        ClaimStatusEvent event = eventCaptor.getValue();
        assertNotNull(event.eventId());
        assertEquals(7L, event.claimId());
        assertEquals(ClaimStatus.DRAFT, event.previousStatus());
        assertEquals(ClaimStatus.SUBMITTED, event.newStatus());
        assertEquals("claimant@example.com", event.changedBy());
        assertEquals(history.getChangedAt(), event.occurredAt());
    }

    @Test
    void supportsRejectedReviewBranch() {
        Claim claim = claim(2L);
        claim.transitionTo(ClaimStatus.SUBMITTED);
        claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        when(claimRepository.findById(2L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);

        ClaimDTO result = claimService.transitionClaimStatus(
                2L, ClaimStatus.REJECTED, "officer", "Missing documents");

        assertEquals(ClaimStatus.REJECTED, result.getClaimStatus());
        verify(claimStatusHistoryRepository).save(any(ClaimStatusHistory.class));
    }

    @Test
    void rejectsInvalidTransitionWithoutWritingClaimOrHistory() {
        Claim claim = claim(3L);
        when(claimRepository.findById(3L)).thenReturn(Optional.of(claim));

        assertThrows(InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus(3L, ClaimStatus.APPROVED, "officer", null));

        assertEquals(ClaimStatus.DRAFT, claim.getClaimStatus());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(claimStatusHistoryRepository, never()).save(any(ClaimStatusHistory.class));
        verify(claimStatusEventPublisher, never()).publishAfterCommit(any(ClaimStatusEvent.class));
    }

    @Test
    void requiresAnAuditableActorForEveryTransition() {
        assertThrows(IllegalArgumentException.class,
                () -> claimService.transitionClaimStatus(3L, ClaimStatus.SUBMITTED, " ", null));

        verify(claimRepository, never()).findById(anyLong());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(claimStatusHistoryRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void throwsMeaningfulExceptionWhenClaimDoesNotExist() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        ClaimNotFoundException exception = assertThrows(ClaimNotFoundException.class,
                () -> claimService.transitionClaimStatus(99L, ClaimStatus.SUBMITTED, "claimant", null));

        assertEquals("Claim not found with id: 99", exception.getMessage());
        verify(claimStatusHistoryRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void rejectsClaimCreationForUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        ClaimCreateRequest request = new ClaimCreateRequest(
                404L, new BigDecimal("10.00"), "MEDICAL", "Treatment", "user@example.com");

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> claimService.createClaim(request));

        assertEquals("User not found with id: 404", exception.getMessage());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void preventsClaimantFromCreatingClaimForAnotherUser() {
        User owner = user(7L, "owner");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(owner));
        ClaimCreateRequest request = new ClaimCreateRequest(
                7L, new BigDecimal("10.00"), "MEDICAL", "Treatment", "owner@example.com");

        assertThrows(UnauthorizedClaimAccessException.class,
                () -> claimService.createClaimForClaimant(
                        request, "another-user", "create-owner-7"));

        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void preventsClaimantFromViewingAnotherUsersClaim() {
        Claim claim = claim(8L);
        claim.setUser(user(7L, "owner"));
        when(claimRepository.findById(8L)).thenReturn(Optional.of(claim));

        assertThrows(UnauthorizedClaimAccessException.class,
                () -> claimService.getClaimForActor(8L, "another-user", false));
    }

    @Test
    void permitsOfficerToViewAnyClaim() {
        Claim claim = claim(9L);
        claim.setUser(user(7L, "owner"));
        when(claimRepository.findById(9L)).thenReturn(Optional.of(claim));

        ClaimDTO result = claimService.getClaimForActor(9L, "officer", true);

        assertEquals(9L, result.getClaimId());
        assertEquals(7L, result.getUserId());
    }

    @Test
    void onlyOwnerCanSubmitClaim() {
        Claim claim = claim(10L);
        claim.setUser(user(7L, "owner"));
        when(claimRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);

        ClaimDTO result = claimService.submitClaimForClaimant(10L, "owner", "submit-10");

        assertEquals(ClaimStatus.SUBMITTED, result.getClaimStatus());
        verify(claimStatusHistoryRepository).save(any(ClaimStatusHistory.class));
    }

    @Test
    void storesCreationIdempotencyKeyOnNewClaim() {
        User owner = user(7L, "owner");
        ClaimCreateRequest request = request(new BigDecimal("100.00"), "Treatment");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(owner));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> {
            Claim saved = invocation.getArgument(0);
            saved.setClaimId(21L);
            return saved;
        });

        ClaimDTO result = claimService.createClaimForClaimant(request, "owner", " create-21 ");

        assertEquals(21L, result.getClaimId());
        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertEquals("create-21", claimCaptor.getValue().getIdempotencyKey());
    }

    @Test
    void returnsExistingClaimForMatchingCreationRetryWithoutWriting() {
        Claim existing = claimFromRequest(22L, request(new BigDecimal("100.00"), "Treatment"));
        existing.setIdempotencyKey("create-22");
        existing.transitionTo(ClaimStatus.SUBMITTED);
        existing.setSubmissionIdempotencyKey("submit-22");
        when(claimRepository.findByIdempotencyKey("create-22")).thenReturn(Optional.of(existing));

        ClaimDTO result = claimService.createClaimForClaimant(
                request(new BigDecimal("100.0"), "Treatment"), "owner", "create-22");

        assertEquals(22L, result.getClaimId());
        assertEquals(ClaimStatus.SUBMITTED, result.getClaimStatus());
        verify(userRepository, never()).findById(any());
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void concurrentCreationRetryIsFoundAfterOwnerLockWithoutWriting() {
        ClaimCreateRequest request = request(new BigDecimal("100.00"), "Treatment");
        Claim committedByFirstRequest = claimFromRequest(28L, request);
        committedByFirstRequest.setIdempotencyKey("create-28");
        when(claimRepository.findByIdempotencyKey("create-28"))
                .thenReturn(Optional.empty(), Optional.of(committedByFirstRequest));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L, "owner")));

        ClaimDTO retry = claimService.createClaimForClaimant(request, "owner", "create-28");

        assertEquals(28L, retry.getClaimId());
        verify(claimRepository, times(2)).findByIdempotencyKey("create-28");
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void rejectsCreationKeyReuseWithDifferentPayload() {
        Claim existing = claimFromRequest(23L, request(new BigDecimal("100.00"), "Treatment"));
        existing.setIdempotencyKey("create-23");
        when(claimRepository.findByIdempotencyKey("create-23")).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> claimService.createClaimForClaimant(
                        request(new BigDecimal("125.00"), "Treatment"), "owner", "create-23"));

        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void repeatedSubmissionWithSameKeyDoesNotRepeatTransition() {
        Claim claim = claim(24L);
        claim.setUser(user(7L, "owner"));
        when(claimRepository.findByIdForUpdate(24L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);

        ClaimDTO first = claimService.submitClaimForClaimant(24L, "owner", "submit-24");
        ClaimDTO retry = claimService.submitClaimForClaimant(24L, "owner", "submit-24");

        assertEquals(ClaimStatus.SUBMITTED, first.getClaimStatus());
        assertEquals(ClaimStatus.SUBMITTED, retry.getClaimStatus());
        verify(claimRepository).save(claim);
        verify(claimStatusHistoryRepository).save(any(ClaimStatusHistory.class));
    }

    @Test
    void rejectsDifferentSubmissionKeyAfterClaimWasSubmitted() {
        Claim claim = claim(25L);
        claim.setUser(user(7L, "owner"));
        claim.transitionTo(ClaimStatus.SUBMITTED);
        claim.setSubmissionIdempotencyKey("submit-25-original");
        when(claimRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(claim));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> claimService.submitClaimForClaimant(25L, "owner", "submit-25-other"));

        verify(claimRepository, never()).save(any(Claim.class));
        verify(claimStatusHistoryRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void rejectsSubmissionKeyAlreadyUsedByAnotherClaim() {
        Claim claim = claim(26L);
        claim.setUser(user(7L, "owner"));
        Claim otherClaim = claim(27L);
        when(claimRepository.findByIdForUpdate(26L)).thenReturn(Optional.of(claim));
        when(claimRepository.findBySubmissionIdempotencyKey("shared-submit"))
                .thenReturn(Optional.of(otherClaim));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> claimService.submitClaimForClaimant(26L, "owner", "shared-submit"));

        verify(claimRepository, never()).save(any(Claim.class));
        verify(claimStatusHistoryRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void rejectsCreationKeyReusedForSubmission() {
        Claim claim = claim(29L);
        claim.setUser(user(7L, "owner"));
        Claim claimWithCreationKey = claim(30L);
        when(claimRepository.findByIdForUpdate(29L)).thenReturn(Optional.of(claim));
        when(claimRepository.findByIdempotencyKey("create-30"))
                .thenReturn(Optional.of(claimWithCreationKey));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> claimService.submitClaimForClaimant(29L, "owner", "create-30"));

        verify(claimRepository, never()).save(any(Claim.class));
        verify(claimStatusHistoryRepository, never()).save(any(ClaimStatusHistory.class));
    }

    @Test
    void legacyBatchTransitionUsesControlledTransitionBeforeCacheAndEvent() {
        Claim claim = claim(4L);
        claim.setEmailId("user@example.com");
        when(claimRepository.findById(4L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);
        claimService.updateClaimStatus(4L, "submitted", "user@example.com");

        assertEquals(ClaimStatus.SUBMITTED, claim.getClaimStatus());
        verify(claimStatusHistoryRepository).save(any(ClaimStatusHistory.class));
        verify(claimCacheService).evictClaimAfterCommit(4L);
        ArgumentCaptor<ClaimStatusEvent> eventCaptor = ArgumentCaptor.forClass(ClaimStatusEvent.class);
        verify(claimStatusEventPublisher).publishAfterCommit(eventCaptor.capture());
        assertEquals("user@example.com", eventCaptor.getValue().userEmail());
    }

    @Test
    void readsExistingCachedStatus() {
        when(claimCacheService.getStatus(1L)).thenReturn(Optional.of("APPROVED"));

        assertEquals("APPROVED", claimService.getClaimStatus(1L));
        verify(claimRepository, never()).findStatusByClaimId(1L);
    }

    @Test
    void readsClaimFromCacheWithoutQueryingPostgresql() {
        ClaimDTO cached = claimDto(31L, new BigDecimal("75.00"));
        when(claimCacheService.getClaim(31L)).thenReturn(Optional.of(cached));

        ClaimDTO result = claimService.getClaim(31L);

        assertEquals(cached, result);
        verify(claimRepository, never()).findById(31L);
    }

    @Test
    void populatesClaimCacheAfterPostgresqlMiss() {
        Claim claim = claim(32L);
        claim.setUser(user(7L, "owner"));
        claim.setClaimAmount(new BigDecimal("80.00"));
        when(claimCacheService.getClaim(32L)).thenReturn(Optional.empty());
        when(claimRepository.findById(32L)).thenReturn(Optional.of(claim));

        ClaimDTO result = claimService.getClaim(32L);

        assertEquals(32L, result.getClaimId());
        verify(claimCacheService).putClaim(result);
    }

    @Test
    void populatesStatusCacheAfterPostgresqlMiss() {
        when(claimCacheService.getStatus(33L)).thenReturn(Optional.empty());
        when(claimRepository.findStatusByClaimId(33L)).thenReturn(Optional.of(ClaimStatus.UNDER_REVIEW));

        assertEquals("UNDER_REVIEW", claimService.getClaimStatus(33L));
        verify(claimCacheService).putStatus(33L, "UNDER_REVIEW");
    }

    @Test
    void updateInvalidatesCacheAndNextMissReadsUpdatedDatabaseValue() {
        Claim claim = claim(34L);
        claim.setUser(user(7L, "owner"));
        claim.setClaimAmount(new BigDecimal("50.00"));
        when(claimRepository.findById(34L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);
        when(claimCacheService.getClaim(34L)).thenReturn(Optional.empty());

        ClaimDTO update = claimDto(34L, new BigDecimal("95.00"));
        claimService.updateClaim(34L, update);
        ClaimDTO refreshed = claimService.getClaim(34L);

        assertEquals(new BigDecimal("95.00"), refreshed.getClaimAmount());
        verify(claimCacheService).evictClaimAfterCommit(34L);
        verify(claimCacheService).putClaim(refreshed);
    }

    @Test
    void deletesExistingClaim() {
        Claim claim = claim(5L);
        when(claimRepository.findById(5L)).thenReturn(Optional.of(claim));

        claimService.deleteClaim(5L);

        verify(claimRepository).delete(claim);
        verify(claimCacheService).evictClaimAfterCommit(5L);
    }

    private Claim claim(Long id) {
        Claim claim = new Claim();
        claim.setClaimId(id);
        claim.setClaimType("MEDICAL");
        claim.setDescription("Treatment");
        return claim;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setRole(UserRole.CLAIMANT);
        user.setStatus("active");
        return user;
    }

    private ClaimCreateRequest request(BigDecimal amount, String description) {
        return new ClaimCreateRequest(
                7L, amount, "MEDICAL", description, "owner@example.com");
    }

    private Claim claimFromRequest(Long id, ClaimCreateRequest request) {
        Claim claim = claim(id);
        claim.setUser(user(request.userId(), "owner"));
        claim.setClaimAmount(request.claimAmount());
        claim.setClaimType(request.claimType());
        claim.setDescription(request.description());
        claim.setEmailId(request.emailId());
        return claim;
    }

    private ClaimDTO claimDto(Long id, BigDecimal amount) {
        return new ClaimDTO(id, 7L, "owner@example.com", null, amount,
                "MEDICAL", "Treatment", ClaimStatus.DRAFT, null);
    }
}
