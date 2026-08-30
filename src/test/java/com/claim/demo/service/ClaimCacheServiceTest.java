package com.claim.demo.service;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ClaimCacheService claimCacheService;

    @BeforeEach
    void setUp() {
        claimCacheService = new ClaimCacheService(
                redisTemplate, Duration.ofMinutes(10), Duration.ofMinutes(5));
    }

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void returnsClaimOnCacheHit() {
        ClaimDTO claim = claim(41L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("claims:detail:41")).thenReturn(claim);

        assertSame(claim, claimCacheService.getClaim(41L).orElseThrow());
    }

    @Test
    void appliesConfiguredTtlsWhenPopulatingCaches() {
        ClaimDTO claim = claim(42L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        claimCacheService.putClaim(claim);
        claimCacheService.putStatus(42L, "APPROVED");

        verify(valueOperations).set("claims:detail:42", claim, Duration.ofMinutes(10));
        verify(valueOperations).set("claims:status:42", "APPROVED", Duration.ofMinutes(5));
    }

    @Test
    void treatsRedisFailureAsCacheMiss() {
        when(redisTemplate.opsForValue())
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertTrue(claimCacheService.getClaim(43L).isEmpty());
        assertTrue(claimCacheService.getStatus(43L).isEmpty());
        assertDoesNotThrow(() -> claimCacheService.putClaim(claim(43L)));
    }

    @Test
    void invalidatesDetailAndStatusOnlyAfterCommit() {
        beginTransactionSynchronization();

        claimCacheService.evictClaimAfterCommit(44L);

        verify(redisTemplate, never()).delete(List.of("claims:detail:44", "claims:status:44"));
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();

        verify(redisTemplate).delete(List.of("claims:detail:44", "claims:status:44"));
    }

    @Test
    void doesNotInvalidateWhenTransactionRollsBack() {
        beginTransactionSynchronization();

        claimCacheService.evictClaimAfterCommit(45L);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(redisTemplate, never()).delete(List.of("claims:detail:45", "claims:status:45"));
    }

    @Test
    void evictsImmediatelyWhenNoTransactionExists() {
        claimCacheService.evictClaimAfterCommit(46L);

        verify(redisTemplate).delete(List.of("claims:detail:46", "claims:status:46"));
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private ClaimDTO claim(Long claimId) {
        return new ClaimDTO(claimId, 7L, "owner@example.com", new Date(),
                new BigDecimal("100.00"), "MEDICAL", "Treatment",
                ClaimStatus.DRAFT, new Date());
    }
}
