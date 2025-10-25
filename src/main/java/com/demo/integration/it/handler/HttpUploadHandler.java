package com.demo.integration.it.handler;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.guard.GracefulShutdownManager;
import com.demo.integration.it.guard.IdempotencyGuard;
import com.demo.integration.it.model.BatchRequest;
import com.demo.integration.it.service.FailedBatchService;
import com.demo.integration.it.service.ResilientUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.acks.AcknowledgmentCallback;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/*
 * @created by 24/10/2025  - 00:50
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class HttpUploadHandler {

   private final ResilientUploadService uploadService;
   private final FailedBatchService failedBatchService;
   private final IdempotencyGuard idempotencyGuard;
   private final GracefulShutdownManager shutdownManager;
   private final StringRedisTemplate stringRedisTemplate;
   private final FileProcessorProperties properties;
   private final ObjectMapper objectMapper;

   public HttpUploadHandler(ResilientUploadService uploadService,
                            FailedBatchService failedBatchService,
                            IdempotencyGuard idempotencyGuard,
                            GracefulShutdownManager shutdownManager,
                            StringRedisTemplate stringRedisTemplate,
                            FileProcessorProperties properties, ObjectMapper objectMapper) {
      this.uploadService = uploadService;
      this.failedBatchService = failedBatchService;
      this.idempotencyGuard = idempotencyGuard;
      this.shutdownManager = shutdownManager;
      this.stringRedisTemplate = stringRedisTemplate;
      this.properties = properties;
      this.objectMapper = objectMapper;
   }

   @ServiceActivator
   public Message<BatchRequest> uploadBatch(Message<BatchRequest> message) {
      shutdownManager.incrementInFlight();

      message.getHeaders().forEach((k, v) -> log.info("HEADER>>>>>{} = {}", k, v));

      BatchRequest batch = message.getPayload();

      try {
         if (!idempotencyGuard.markAsProcessed(batch.getBatchId())) {
            log.info("Skipping duplicate batch: {}", batch.getBatchId());
            return message;
         }

         log.info("Uploading batch: {}", batch.getBatchId());

         uploadService.uploadBatch(batch).block();

         log.info("Successfully uploaded batch: {}", batch.getBatchId());
         acknowledge(message);


         return MessageBuilder
                 .withPayload(batch)
                 .copyHeaders(message.getHeaders())
                 .build();
      } catch (Exception e) {
         log.error("Failed to upload batch: {}", batch.getBatchId(), e);

         idempotencyGuard.clearProcessedFlag(batch.getBatchId());
         if (e instanceof WebClientResponseException wcr) {
            failedBatchService.logFailedBatch(batch, wcr.getMessage(), String.valueOf(wcr.getStatusCode().value()));
         } else if (e instanceof WebClientRequestException wrq) {
            failedBatchService.logFailedBatch(batch, wrq.getMessage(), "NETWORK_ERROR");
         } else {
            failedBatchService.logFailedBatch(batch, e.getMessage(), "UNKNOWN_ERROR");
         }
         acknowledge(message);
         throw new MessageHandlingException(message, "Upload failed", e);
      } finally {
         shutdownManager.decrementInFlight();
      }
   }

   @SneakyThrows
   private void acknowledge(Message<BatchRequest> message) {
//      String recordId = (String) message.getHeaders().get("redis_streamMessageId");
      RecordId recordId = (RecordId) message.getHeaders().get("redis_streamMessageId");
      stringRedisTemplate.opsForStream().acknowledge(properties.getRedisQueue().getStreamKey(), properties.getRedisQueue().getConsumerGroup(), recordId);
      log.info("Acknowledged Redis record ID: {}", recordId);
   }
}
