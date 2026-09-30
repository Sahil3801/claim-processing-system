package com.claim.demo.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the outbox relay. Kept separate so tests and tools can disable polling while still
 * invoking the relay directly.
 */
@Component
@ConditionalOnProperty(name = "claims.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class ClaimStatusOutboxScheduler {

    private final ClaimStatusOutboxRelay relay;

    public ClaimStatusOutboxScheduler(ClaimStatusOutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.relay.interval:PT1S}")
    public void relayPendingEvents() {
        // Drain a backlog (for example after a broker outage) instead of one batch per interval.
        int delivered;
        do {
            delivered = relay.publishPendingBatch();
        } while (delivered > 0 && !Thread.currentThread().isInterrupted());
    }

    @Scheduled(cron = "${claims.outbox.cleanup-cron:0 17 3 * * *}")
    public void deleteExpiredPublishedEvents() {
        relay.deleteExpiredPublishedEvents();
    }
}
