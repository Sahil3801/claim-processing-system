package com.claim.demo.service;

import com.claim.demo.entity.ClaimStatusOutboxEvent;
import com.claim.demo.event.ClaimStatusEvent;
import com.claim.demo.repository.ClaimStatusOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records claim-status events in the transactional outbox. The event commits or rolls back
 * together with the claim change; {@link ClaimStatusOutboxRelay} delivers it to Kafka.
 */
@Service
public class ClaimStatusOutbox {

    private final ClaimStatusOutboxRepository outboxRepository;

    public ClaimStatusOutbox(ClaimStatusOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(ClaimStatusEvent event) {
        outboxRepository.save(ClaimStatusOutboxEvent.from(event));
    }
}
