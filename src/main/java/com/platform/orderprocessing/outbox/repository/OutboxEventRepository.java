package com.platform.orderprocessing.outbox.repository;

import com.platform.orderprocessing.outbox.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for transactional outbox persistence.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    @Query("SELECT o FROM OutboxEvent o WHERE o.aggregateId = :aggregateId ORDER BY o.createdAt ASC")
    List<OutboxEvent> findByAggregateId(@Param("aggregateId") String aggregateId);
}
