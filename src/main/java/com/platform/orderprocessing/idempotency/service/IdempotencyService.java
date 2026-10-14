package com.platform.orderprocessing.idempotency.service;

import com.platform.orderprocessing.common.error.IdempotencyConflictException;
import com.platform.orderprocessing.idempotency.domain.IdempotencyRecord;
import com.platform.orderprocessing.idempotency.repository.IdempotencyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing request idempotency to guarantee exactly-once order placement.
 */
@Service
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository idempotencyRepository;
    private final Duration retentionDuration;

    public IdempotencyService(IdempotencyRepository idempotencyRepository,
                              @Value("${platform.idempotency.retention-hours:24}") int retentionHours) {
        this.idempotencyRepository = idempotencyRepository;
        this.retentionDuration = Duration.ofHours(retentionHours);
    }

    /**
     * Checks if an idempotency key is already recorded.
     * Returns an existing completed record for idempotent replay, or creates an IN_PROGRESS lock.
     *
     * @param key idempotency key header
     * @param path request endpoint URI
     * @param payload serialized request body
     * @return Optional containing completed record if replayed, or empty if new request started
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> checkOrStart(String key, String path, String payload) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String requestHash = computeSha256(payload);
        Optional<IdempotencyRecord> existing = idempotencyRepository.findById(key);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            if (IdempotencyRecord.STATUS_IN_PROGRESS.equals(record.getStatus())) {
                LOG.warn("Concurrent request detected for idempotency key: {}", key);
                throw new IdempotencyConflictException(
                        "A request with Idempotency-Key '" + key + "' is currently being processed. Please retry shortly.");
            }

            if (!record.getRequestHash().equals(requestHash)) {
                LOG.warn("Payload mismatch for reused idempotency key: {}", key);
                throw new IdempotencyConflictException(
                        "Idempotency-Key '" + key + "' was already used with a different request payload.");
            }

            LOG.info("Idempotent replay detected for key: {}. Returning cached response.", key);
            return Optional.of(record);
        }

        Instant expiresAt = Instant.now().plus(retentionDuration);
        IdempotencyRecord newRecord = new IdempotencyRecord(key, path, requestHash, expiresAt);
        idempotencyRepository.saveAndFlush(newRecord);
        LOG.debug("Registered new in-progress idempotency key: {}", key);
        return Optional.empty();
    }

    /**
     * Marks an idempotency key as completed with the rendered response body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String key, int responseCode, String responseBody, UUID orderId) {
        if (key == null || key.isBlank()) {
            return;
        }

        idempotencyRepository.findById(key).ifPresent(record -> {
            record.markCompleted(responseCode, responseBody, orderId);
            idempotencyRepository.save(record);
            LOG.debug("Completed idempotency record for key: {}, orderId: {}", key, orderId);
        });
    }

    /**
     * Releases or deletes an in-progress record if the request failed validation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key) {
        if (key != null && !key.isBlank()) {
            idempotencyRepository.deleteById(key);
            LOG.debug("Released idempotency key due to error: {}", key);
        }
    }

    /**
     * Purges expired idempotency records.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredRecords() {
        int deleted = idempotencyRepository.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            LOG.info("Cleaned up {} expired idempotency records", deleted);
        }
    }

    public static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }
}
