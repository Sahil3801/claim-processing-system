package com.claim.demo.repository;

import com.claim.demo.entity.ClaimStatusOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClaimStatusOutboxRepository extends JpaRepository<ClaimStatusOutboxEvent, Long> {

    /**
     * Locks the oldest unpublished events. SKIP LOCKED lets several application instances
     * relay concurrently without sending the same row twice or waiting on each other.
     */
    @Query(value = """
            SELECT *
            FROM claim_status_outbox
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ClaimStatusOutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Query("delete from ClaimStatusOutboxEvent e where e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
