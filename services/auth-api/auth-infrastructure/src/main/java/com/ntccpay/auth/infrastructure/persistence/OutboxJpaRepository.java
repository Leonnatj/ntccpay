package com.ntccpay.auth.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Spring Data surface for the transactional outbox. */
public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    Page<OutboxEventEntity> findByPublishedAtIsNullOrderByOccurredAtAsc(Pageable pageable);

    /**
     * Marks a row published only if it is still unpublished. Returns 0 when a
     * concurrent poller already won the race — the row is not re-resent.
     */
    @Modifying
    @Transactional
    @Query("""
            update OutboxEventEntity o
            set o.publishedAt = :publishedAt
            where o.id = :id and o.publishedAt is null
            """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}