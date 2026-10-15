package com.platform.orderprocessing.outbox.controller;

import com.platform.orderprocessing.outbox.domain.OutboxEvent;
import com.platform.orderprocessing.outbox.repository.OutboxEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for inspecting transactional outbox events and delivery status.
 */
@RestController
@RequestMapping("/api/v1/outbox")
@Tag(name = "Transactional Outbox", description = "Monitor pending and published outbox events")
public class OutboxController {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxController(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @GetMapping("/events")
    @Operation(summary = "Get recent outbox events", description = "Returns most recent outbox records for real-time monitoring")
    public ResponseEntity<List<OutboxEvent>> getRecentEvents(@RequestParam(defaultValue = "25") int limit) {
        PageRequest pageRequest = PageRequest.of(0, Math.min(limit, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(outboxEventRepository.findAll(pageRequest).getContent());
    }
}
