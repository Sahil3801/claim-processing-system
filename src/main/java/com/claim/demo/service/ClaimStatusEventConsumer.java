package com.claim.demo.service;

import com.claim.demo.entity.ProcessedKafkaEvent;
import com.claim.demo.event.ClaimStatusEvent;
import com.claim.demo.repository.ProcessedKafkaEventRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ClaimStatusEventConsumer {

    private static final Logger logger = LogManager.getLogger(ClaimStatusEventConsumer.class);

    private final ProcessedKafkaEventRepository processedEventRepository;
    private final EmailService emailService;

    public ClaimStatusEventConsumer(
            ProcessedKafkaEventRepository processedEventRepository,
            EmailService emailService) {
        this.processedEventRepository = processedEventRepository;
        this.emailService = emailService;
    }

    @Transactional
    @KafkaListener(
            topics = "${claims.kafka.topics.claim-status:claims.status.v1}",
            groupId = "${spring.kafka.consumer.group-id:claims-notifications-v1}",
            containerFactory = "claimStatusKafkaListenerContainerFactory",
            autoStartup = "${spring.kafka.listener.auto-startup:true}")
    public void consume(ClaimStatusEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            logger.debug("Skipping already processed claim-status event {}", event.eventId());
            return;
        }

        // Flush the unique event ID before the side effect. Concurrent duplicate deliveries
        // then serialize on the primary key; a notification exception rolls this marker back.
        processedEventRepository.saveAndFlush(new ProcessedKafkaEvent(
                event.eventId(), event.claimId(), Instant.now()));

        if (event.userEmail() != null && !event.userEmail().isBlank()) {
            emailService.sendEmail(
                    event.userEmail(),
                    "Claim Status Update",
                    "Your claim #" + event.claimId() + " has been updated from "
                            + event.previousStatus() + " to " + event.newStatus());
        }
    }
}
