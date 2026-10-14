package com.platform.orderprocessing.idempotency.repository;

import com.platform.orderprocessing.idempotency.domain.IdempotencyRecord;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for idempotency records.
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :cutoff")
    int deleteExpiredRecords(@Param("cutoff") Instant cutoff);
}
