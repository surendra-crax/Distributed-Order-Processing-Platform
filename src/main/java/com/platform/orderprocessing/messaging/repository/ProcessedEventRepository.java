package com.platform.orderprocessing.messaging.repository;

import com.platform.orderprocessing.messaging.domain.ProcessedEvent;
import com.platform.orderprocessing.messaging.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for consumer event deduplication records.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
