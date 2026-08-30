package com.claim.demo.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimCreateRequest;
import com.claim.demo.dto.ClaimDTO;
import com.claim.demo.entity.Claim;
import com.claim.demo.entity.ClaimStatusHistory;
import com.claim.demo.entity.User;
import com.claim.demo.event.ClaimStatusEvent;
import com.claim.demo.exception.ClaimNotFoundException;
import com.claim.demo.exception.IdempotencyKeyConflictException;
import com.claim.demo.exception.UserNotFoundException;
import com.claim.demo.exception.UnauthorizedClaimAccessException;
import com.claim.demo.repository.ClaimRepository;
import com.claim.demo.repository.ClaimStatusHistoryRepository;
import com.claim.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository claimStatusHistoryRepository;
    private final UserRepository userRepository;
    private final ClaimStatusEventPublisher claimStatusEventPublisher;
    private final ClaimCacheService claimCacheService;

	private static final Logger logger = LogManager.getLogger(ClaimService.class);

    public ClaimService(ClaimRepository claimRepository,
                        ClaimStatusHistoryRepository claimStatusHistoryRepository,
                        UserRepository userRepository,
                        ClaimStatusEventPublisher claimStatusEventPublisher,
                        ClaimCacheService claimCacheService) {
        this.claimRepository = claimRepository;
        this.claimStatusHistoryRepository = claimStatusHistoryRepository;
        this.userRepository = userRepository;
        this.claimStatusEventPublisher = claimStatusEventPublisher;
        this.claimCacheService = claimCacheService;
    }

    @Transactional
    public ClaimDTO createClaim(ClaimCreateRequest request) {
        return createClaim(request, null, null);
    }

    @Transactional
    public ClaimDTO createClaimForClaimant(
            ClaimCreateRequest request, String username, String idempotencyKey) {
        return createClaim(request, username, normalizeIdempotencyKey(idempotencyKey));
    }

    /**
     * The creation key remains on the claim permanently so a create retry still resolves
     * after the claim has been submitted. A separate submission key records that operation.
     * Locking the owner row serializes concurrent creation retries for the same request.
     */
    private ClaimDTO createClaim(
            ClaimCreateRequest request, String requiredOwner, String idempotencyKey) {
        if (idempotencyKey != null) {
            Claim existingClaim = findExistingCreation(request, requiredOwner, idempotencyKey);
            if (existingClaim != null) {
                return convertToDTO(existingClaim);
            }
        }

        User user = (idempotencyKey == null
                ? userRepository.findById(request.userId())
                : userRepository.findByIdForUpdate(request.userId()))
                .orElseThrow(() -> new UserNotFoundException(request.userId()));
        if (requiredOwner != null && !requiredOwner.equals(user.getUsername())) {
            throw new UnauthorizedClaimAccessException(
                    "User " + requiredOwner + " cannot create a claim for user " + request.userId());
        }

        // Recheck after taking the owner lock; another concurrent retry may just have committed.
        if (idempotencyKey != null) {
            Claim existingClaim = findExistingCreation(request, requiredOwner, idempotencyKey);
            if (existingClaim != null) {
                return convertToDTO(existingClaim);
            }
        }

        Claim claim = new Claim();
        claim.setUser(user);
        claim.setClaimAmount(request.claimAmount());
        claim.setClaimType(request.claimType());
        claim.setDescription(request.description());
        claim.setEmailId(request.emailId());
        claim.setIdempotencyKey(idempotencyKey);
        return submitClaim(claim);
    }

    @Transactional(readOnly = true)
    public ClaimDTO getClaim(Long claimId) {
        return findClaimCacheAside(claimId);
    }

    @Transactional(readOnly = true)
    public ClaimDTO getClaimForActor(Long claimId, String username, boolean canViewAnyClaim) {
        ClaimDTO claim = findClaimCacheAside(claimId);
        if (!canViewAnyClaim) {
            requireOwner(claim, username);
        }
        return claim;
    }

    @Transactional
    public ClaimDTO submitClaimForClaimant(Long claimId, String username, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Claim claim = findClaimByIdForUpdate(claimId);
        requireOwner(claim, username);

        if (normalizedKey.equals(claim.getSubmissionIdempotencyKey())) {
            return convertToDTO(claim);
        }
        if (claim.getSubmissionIdempotencyKey() != null
                || claimRepository.findByIdempotencyKey(normalizedKey).isPresent()
                || claimRepository.findBySubmissionIdempotencyKey(normalizedKey).isPresent()) {
            throw new IdempotencyKeyConflictException(normalizedKey);
        }

        claim.setSubmissionIdempotencyKey(normalizedKey);
        return convertToDTO(applyTransition(claim, ClaimStatus.SUBMITTED, username, null));
    }

    @Transactional(readOnly = true)
    public Page<ClaimDTO> findClaimsByUsername(String username, Pageable pageable) {
        return claimRepository.findByUser_Username(username, pageable).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ClaimDTO> findClaims(ClaimStatus status, String claimType, Long userId, Pageable pageable) {
        Specification<Claim> filters = Specification.where(null);
        if (status != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("claimStatus"), status));
        }
        if (claimType != null && !claimType.isBlank()) {
            String normalizedType = claimType.trim().toLowerCase(Locale.ROOT);
            filters = filters.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("claimType")), normalizedType));
        }
        if (userId != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("user").get("userId"), userId));
        }
        return claimRepository.findAll(filters, pageable).map(this::convertToDTO);
    }

    @Transactional
    public ClaimDTO transitionClaimStatus(Long claimId, ClaimStatus newStatus, String changedBy, String reason) {
        return convertToDTO(applyTransition(claimId, newStatus, changedBy, reason));
    }

    /**
     * Compatibility entry point for the existing scheduled job. New API operations should
     * call {@link #transitionClaimStatus(Long, ClaimStatus, String, String)} with the real actor.
     */
    @Transactional
	public void updateClaimStatus(Long claimId, String newStatus, String userEmail) {
        ClaimStatus targetStatus = ClaimStatus.from(newStatus);
        Claim claim = applyTransition(claimId, targetStatus, "SYSTEM_BATCH", null);

        logger.info("Completed scheduled claim status update. Claim ID: {}", claim.getClaimId());
	}

    private Claim applyTransition(Long claimId, ClaimStatus newStatus, String changedBy, String reason) {
        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("changedBy is required for a claim status transition");
        }

        Claim claim = findClaimById(claimId);
        return applyTransition(claim, newStatus, changedBy, reason);
    }

    private Claim applyTransition(Claim claim, ClaimStatus newStatus, String changedBy, String reason) {
        Long claimId = claim.getClaimId();
        ClaimStatus previousStatus = claim.transitionTo(newStatus);
        claim.setLastUpdated(new Date());
        Claim savedClaim = claimRepository.save(claim);
        Instant occurredAt = Instant.now();

        claimStatusHistoryRepository.save(new ClaimStatusHistory(
                claimId,
                previousStatus,
                newStatus,
                changedBy,
                reason,
                occurredAt));
        claimCacheService.evictClaimAfterCommit(claimId);
        claimStatusEventPublisher.publishAfterCommit(ClaimStatusEvent.create(
                claimId,
                previousStatus,
                newStatus,
                claim.getUser() == null ? null : claim.getUser().getUserId(),
                notificationEmail(claim),
                changedBy,
                occurredAt));

        logger.debug("Claim status updated successfully. Claim ID: {}, Old Status: {}, New Status: {}",
                claimId, previousStatus, newStatus);
        return savedClaim;
    }

    private String notificationEmail(Claim claim) {
        if (claim.getEmailId() != null && !claim.getEmailId().isBlank()) {
            return claim.getEmailId();
        }
        return claim.getUser() == null ? null : claim.getUser().getEmail();
    }

    private void requireOwner(Claim claim, String username) {
        if (claim.getUser() == null || !username.equals(claim.getUser().getUsername())) {
            throw new UnauthorizedClaimAccessException(claim.getClaimId(), username);
        }
    }

    private void requireOwner(ClaimDTO claim, String username) {
        User actor = userRepository.findByUsername(username);
        if (actor == null || !Objects.equals(actor.getUserId(), claim.getUserId())) {
            throw new UnauthorizedClaimAccessException(claim.getClaimId(), username);
        }
    }

    @Transactional(readOnly = true)
    public String getClaimStatus(Long claimId) {
        return claimCacheService.getStatus(claimId).orElseGet(() -> {
            String status = claimRepository.findStatusByClaimId(claimId)
                    .orElseThrow(() -> new ClaimNotFoundException(claimId))
                    .name();
            claimCacheService.putStatus(claimId, status);
            return status;
        });
    }

	@Transactional
	public ClaimDTO submitClaim(Claim claim) {
		Date now = new Date();
		claim.setClaimDate(now);
		claim.setLastUpdated(now);
		return convertToDTO(claimRepository.save(claim));
	}

	@Transactional(readOnly = true)
	public Claim findClaimById(Long claimId) {
		return claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
	}

    private Claim findClaimByIdForUpdate(Long claimId) {
        return claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
    }

    private ClaimDTO findClaimCacheAside(Long claimId) {
        return claimCacheService.getClaim(claimId).orElseGet(() -> {
            ClaimDTO claim = convertToDTO(findClaimById(claimId));
            claimCacheService.putClaim(claim);
            return claim;
        });
    }

    private boolean matchesCreateRequest(Claim claim, ClaimCreateRequest request) {
        return claim.getUser() != null
                && Objects.equals(claim.getUser().getUserId(), request.userId())
                && claim.getClaimAmount() != null
                && claim.getClaimAmount().compareTo(request.claimAmount()) == 0
                && Objects.equals(claim.getClaimType(), request.claimType())
                && Objects.equals(claim.getDescription(), request.description())
                && Objects.equals(claim.getEmailId(), request.emailId());
    }

    private Claim findExistingCreation(
            ClaimCreateRequest request, String requiredOwner, String idempotencyKey) {
        if (claimRepository.findBySubmissionIdempotencyKey(idempotencyKey).isPresent()) {
            throw new IdempotencyKeyConflictException(idempotencyKey);
        }

        Claim existingClaim = claimRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingClaim == null) {
            return null;
        }
        if (!matchesCreateRequest(existingClaim, request)) {
            throw new IdempotencyKeyConflictException(idempotencyKey);
        }
        if (requiredOwner != null) {
            requireOwner(existingClaim, requiredOwner);
        }
        return existingClaim;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null ? null : idempotencyKey.trim();
    }

	@Transactional
	public ClaimDTO updateClaim(Long claimId, ClaimDTO updatedClaim) {
		Claim claim = findClaimById(claimId);
		claim.setClaimAmount(updatedClaim.getClaimAmount());
		claim.setClaimType(updatedClaim.getClaimType());
		claim.setLastUpdated(new Date());
        ClaimDTO savedClaim = convertToDTO(claimRepository.save(claim));
        claimCacheService.evictClaimAfterCommit(claimId);
		return savedClaim;
	}

	@Transactional
	public void deleteClaim(Long claimId) {
		Claim claim = findClaimById(claimId);
		claimRepository.delete(claim);
        claimCacheService.evictClaimAfterCommit(claimId);
	}

	@Transactional(readOnly = true)
	public List<ClaimDTO> findClaimsByUserId(Long userId) {
		List<Claim> claims = claimRepository.findByUser_UserId(userId);
		return claims.stream().map(this::convertToDTO).collect(Collectors.toList());
	}

	public ClaimDTO convertToDTO(Claim claim) {
		return new ClaimDTO(claim.getClaimId(), claim.getUser() != null ? claim.getUser().getUserId() : null,
				claim.getEmailId(), claim.getClaimDate(), claim.getClaimAmount(), claim.getClaimType(),
				claim.getDescription(), claim.getClaimStatus(),
				claim.getLastUpdated());
	}

	public List<Claim> findClaimsNeedingUpdate() {
		// Fetch claims based on specific criteria, e.g., status is 'Pending' and last
		// updated > 24 hours ago
		return claimRepository.findByClaimStatusAndLastUpdatedBefore(ClaimStatus.SUBMITTED,
				new Date(System.currentTimeMillis() - 86400000));
	}


}
