package com.ntccpay.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Spring Data surface for the Authorization persistence model. */
public interface AuthorizationJpaRepository extends JpaRepository<AuthorizationEntity, UUID> {

    @Query("""
            select a from AuthorizationEntity a
            join a.idempotencyKey k
            where k.idempotencyKey = :key
            """)
    Optional<AuthorizationEntity> findByIdempotencyKeyValue(@Param("key") String key);
}
