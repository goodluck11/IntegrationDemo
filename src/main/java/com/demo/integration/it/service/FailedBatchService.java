package com.demo.integration.it.service;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.model.BatchRequest;
import com.demo.integration.it.model.FailedBatch;
import com.demo.integration.it.repository.FailedBatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.demo.integration.it.model.FailedBatch.BatchStatus.*;

/*
 * @created by 24/10/2025  - 00:51
 * @project IntegrationDemo
 * @author Goodluck
 */
@Service
@Slf4j
public class FailedBatchService {

   private final FailedBatchRepository repository;
   private final ObjectMapper objectMapper;
   private final FileProcessorProperties properties;
   private final String instanceId;

   public FailedBatchService(FailedBatchRepository repository, ObjectMapper objectMapper, FileProcessorProperties properties) {
      this.repository = repository;
      this.objectMapper = objectMapper;
      this.properties = properties;
      this.instanceId = generateInstanceId();
   }

   private String generateInstanceId() {
      try {
         return InetAddress.getLocalHost().getHostName() + "-" +
                 ManagementFactory.getRuntimeMXBean().getName();
      } catch (Exception e) {
         return UUID.randomUUID().toString();
      }
   }

   @Transactional
   public void logFailedBatch(BatchRequest batch, String errorMessage, String errorCode) {
      FailedBatch failedBatch = new FailedBatch();
      failedBatch.setBatchId(batch.getBatchId());
      failedBatch.setFileName(batch.getSourceFileName());
      failedBatch.setRequestId(batch.getRequestId());
      failedBatch.setPayload(serializeToJson(batch));
      failedBatch.setRetryCount(0);
      failedBatch.setMaxRetries(properties.getMaxRetries());
      failedBatch.setStatus(PENDING);
      failedBatch.setErrorMessage(errorMessage);
      failedBatch.setErrorCode(errorCode);
      failedBatch.setCreatedAt(Instant.now());
      failedBatch.setUpdatedAt(Instant.now());
      failedBatch.setNextRetryAt(calculateNextRetry(0));

      repository.save(failedBatch);
      log.info("Logged failed batch to database: {}", batch.getBatchId());

      // Alert on specific error codes
      if (shouldAlert(errorCode)) {
      }
   }

   @Transactional
   public List<FailedBatch> lockAndGetBatchesForRetry(int batchSize) {
      Instant now = Instant.now();
      Instant lockTimeout = now.minus(properties.getRetryLockTimeoutMs(), ChronoUnit.MILLIS);

      int locked = repository.lockBatchesAtomic(instanceId, now, lockTimeout, batchSize);
      if (locked == 0) return Collections.emptyList();

      var lockedBatches = repository.findByLockedByAndStatus(instanceId, LOCKED);

      log.info("Instance {} locked {} batches", instanceId, lockedBatches.size());
      return lockedBatches;
   }

   @Transactional
   public void markRetrying(FailedBatch batch) {
      batch.setStatus(RETRYING);
      batch.setRetryCount(batch.getRetryCount() + 1);
      batch.setUpdatedAt(Instant.now());
      batch.setNextRetryAt(calculateNextRetry(batch.getRetryCount()));
      repository.save(batch);
   }

   @Transactional
   public void markSuccess(FailedBatch batch) {
      batch.setStatus(SUCCESS);
      repository.save(batch);
      log.info("Batch {} succeeded after {} retries", batch.getBatchId(), batch.getRetryCount());
   }

   @Transactional
   public void markFailed(FailedBatch batch, String errorMessage, String errorCode) {
      batch.setStatus(FAILED);
      batch.setErrorMessage(errorMessage);
      batch.setErrorCode(errorCode);
      batch.setUpdatedAt(Instant.now());
      batch.setLockedBy(null);
      batch.setLockedAt(null);
      repository.save(batch);

      log.error("Batch {} failed permanently after {} retries", batch.getBatchId(), batch.getRetryCount());

      // Send alert on permanent failure
   }

   @Transactional
   public void unlockBatch(FailedBatch batch) {
      batch.setStatus(PENDING);
      batch.setLockedBy(null);
      batch.setLockedAt(null);
      batch.setNextRetryAt(calculateNextRetry(batch.getRetryCount()));
      repository.save(batch);
   }

   private Instant calculateNextRetry(int retryCount) {
      long delayMs = properties.getRetryDelayMs() * (long) Math.pow(2, retryCount);
      long maxDelay = properties.getMaxRetryDelayMs();
      delayMs = Math.min(delayMs, maxDelay);

      return Instant.now().plusMillis(delayMs);
   }

   private boolean shouldAlert(String errorCode) {
      return Arrays.asList(properties.getAlertErrorCodes().split(",")).contains(errorCode);
   }

   @SneakyThrows
   private String serializeToJson(BatchRequest batch) {
      return objectMapper.writeValueAsString(batch);
   }
}
