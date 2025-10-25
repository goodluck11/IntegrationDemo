package com.demo.integration.it.handler;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.constant.AppConstants;
import com.demo.integration.it.model.BatchRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/*
 * @created by 24/10/2025  - 14:37
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisBatchWriterHandler {
   private final ObjectMapper objectMapper;
   private final StringRedisTemplate redisTemplate;
   private final FileProcessorProperties properties;

   @ServiceActivator
   public Message<BatchRequest> pushToRedis(Message<BatchRequest> message) {
      BatchRequest batch = message.getPayload();
      boolean success = false;
      String errorMessage;

      try {
         String json = objectMapper.writeValueAsString(batch);
         RecordId recordId = redisTemplate.opsForStream().add(
                 properties.getRedisQueue().getStreamKey(),
                 Map.of("batch", json)
         );

         log.info("Pushed batch {} to Redis queue with ID: {}", batch.getBatchId(), recordId);
         success = true;
         return MessageBuilder
                 .withPayload(batch)
                 .copyHeaders(message.getHeaders())
                 .setHeader(AppConstants.BATCH_FAILED_HEADER, false)
                 .build();
      } catch (JsonProcessingException e) {
         log.error("Failed to serialize batch to JSON: {}", batch.getBatchId(), e);
         errorMessage = e.getMessage();
      } catch (Exception e) {
         log.error("Failed to push batch {} to Redis", batch.getBatchId(), e);
         errorMessage = e.getMessage();
      }

      return MessageBuilder
              .withPayload(batch)
              .copyHeaders(message.getHeaders())
              .setHeader(AppConstants.BATCH_FAILED_HEADER, !success)
              .setHeader(AppConstants.BATCH_ERROR_HEADER, errorMessage)
              .setHeader(AppConstants.BATCH_PUBLISH_TIME_HEADER, Instant.now())
              .build();
   }

}
