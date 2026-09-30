package com.claim.demo.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.claim.demo.dto.ClaimDTO;
import com.claim.demo.entity.Claim;

@Service
public class ClaimBatchService {

    private static final Logger logger = LogManager.getLogger(ClaimBatchService.class);

    @Autowired
    private ClaimService claimService; // Service that contains business logic for updating claims

    // This scheduled task runs every hour
    @Scheduled(cron = "0 0 * * * *") // Cron expression for hourly execution
    public void processPendingClaims() {
        List<Claim> claims = claimService.findClaimsNeedingUpdate(); // Fetch claims that need processing
        for (Claim claim : claims) {
            try {
                // Logic to determine the new status; simplified here
                String newStatus = "UNDER_REVIEW";
                claimService.updateClaimStatus(claim.getClaimId(), newStatus, claim.getEmailId());
            } catch (Exception e) {
                // For example an officer already moved the claim; skip it rather than fail the batch.
                logger.warn("Scheduled review skipped claim {}: {}", claim.getClaimId(), e.getMessage());
            }
        }
    }
}
