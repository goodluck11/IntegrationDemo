package com.demo.integration.it.repository;

import com.demo.integration.it.model.FailedBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/*
 * @created by 24/10/2025  - 00:54
 * @project IntegrationDemo
 * @author Goodluck
 */
@Repository
public interface FailedBatchRepository extends JpaRepository<FailedBatch, Long> {
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("""
           SELECT fb FROM FailedBatch fb 
           WHERE fb.status IN ('PENDING', 'RETRYING')
           AND fb.retryCount < fb.maxRetries
           AND (fb.nextRetryAt IS NULL OR fb.nextRetryAt <= :now)
           AND (fb.lockedBy IS NULL OR fb.lockedAt < :lockTimeout)
           ORDER BY fb.createdAt ASC
           """)
   List<FailedBatch> findBatchesReadyForRetry(
           @Param("now") Instant now,
           @Param("lockTimeout") Instant lockTimeout,
           Pageable pageable
   );

   @Modifying
   @Query("""
           UPDATE FailedBatch fb 
           SET fb.lockedBy = :instanceId, 
               fb.lockedAt = :now,
               fb.status = 'LOCKED'
           WHERE fb.id = :id 
           AND (fb.lockedBy IS NULL OR fb.lockedAt < :lockTimeout)
           """)
   int lockBatch(
           @Param("id") Long id,
           @Param("instanceId") String instanceId,
           @Param("now") Instant now,
           @Param("lockTimeout") Instant lockTimeout
   );

   @Modifying
   @Query(value = """
        UPDATE failed_batches 
        SET locked_by = :instanceId, 
            locked_at = :now,
            status = 'LOCKED'
        WHERE id IN (
            SELECT id FROM failed_batches
            WHERE status IN ('PENDING', 'RETRYING')
            AND retry_count < max_retries
            AND (next_retry_at IS NULL OR next_retry_at <= :now)
            AND (locked_by IS NULL OR locked_at < :lockTimeout)
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        """, nativeQuery = true)
   int lockBatchesAtomic(
           @Param("instanceId") String instanceId,
           @Param("now") Instant now,
           @Param("lockTimeout") Instant lockTimeout,
           @Param("limit") int limit
   );

   List<FailedBatch> findByStatusAndRetryCountLessThan(String status, int maxRetries);

   @Query("SELECT fb FROM FailedBatch fb WHERE fb.status = 'PENDING' " +
           "AND fb.updatedAt < :retryAfter AND fb.retryCount < :maxRetries")
   List<FailedBatch> findBatchesReadyForRetry(
           @Param("retryAfter") Instant retryAfter,
           @Param("maxRetries") int maxRetries
   );

   List<FailedBatch> findByStatus(String status);

   List<FailedBatch> findByLockedByAndStatus(String lockedBy, FailedBatch.BatchStatus status);
}
