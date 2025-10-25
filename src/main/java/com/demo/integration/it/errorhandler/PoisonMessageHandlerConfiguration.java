package com.demo.integration.it.errorhandler;

import com.demo.integration.it.config.FileProcessorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/*
 * @created by 24/10/2025  - 00:52
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
@Slf4j
public class PoisonMessageHandlerConfiguration {

   private final FileProcessorProperties properties;
   private final RedisTemplate<String, String> redisTemplate;
   private final ObjectMapper objectMapper;

   public PoisonMessageHandlerConfiguration(FileProcessorProperties properties,
                                            RedisTemplate<String, String> redisTemplate,
                                            ObjectMapper objectMapper) {
      this.properties = properties;
      this.redisTemplate = redisTemplate;
      this.objectMapper = objectMapper;
   }

   @Bean
   @ServiceActivator(inputChannel = "errorChannel")
   public MessageHandler poisonMessageHandler() {
      return message -> {
         Throwable error = (Throwable) message.getPayload();
         Message<?> failedMessage = ((MessagingException) error).getFailedMessage();

         if (failedMessage == null) {
            log.error("No failed message in error", error);
            return;
         }

         Object payload = failedMessage.getPayload();
         Integer retryCount = (Integer) failedMessage.getHeaders()
                 .getOrDefault("retryCount", 0);

         if (retryCount >= properties.getMaxRetries()) {
            log.error("Moving message to DLQ after {} retries", retryCount);
//            moveToDLQ(payload, error);
         } else {
            log.warn("Retrying message (attempt {})", retryCount + 1);
            requeueWithDelay(failedMessage, retryCount + 1);
         }
      };
   }

   private void moveToDLQ(Object payload, Throwable error) {
      try {
         Map<String, Object> dlqEntry = Map.of(
                 "payload", objectMapper.writeValueAsString(payload),
                 "error", error.getMessage(),
                 "timestamp", Instant.now().toString(),
                 "stackTrace", getStackTrace(error)
         );

         String dlqKey = properties.getRedisQueue().getDlqKey();
         redisTemplate.opsForList().rightPush(
                 dlqKey,
                 objectMapper.writeValueAsString(dlqEntry)
         );

         log.error("Moved to DLQ: {}", dlqKey);
         sendAlert("Poison message detected", dlqEntry);

      } catch (Exception e) {
         log.error("Failed to move message to DLQ", e);
      }
   }

   private void requeueWithDelay(Message<?> message, int retryCount) {
      long delayMs = (long) Math.pow(2, retryCount) * 1000;

      Message<?> retryMessage = MessageBuilder
              .fromMessage(message)
              .setHeader("retryCount", retryCount)
              .build();

      CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
              .execute(() -> {
                 try {
                    String json = objectMapper.writeValueAsString(retryMessage.getPayload());
//                    redisTemplate.opsForStream().add(
//                            properties.getRedisQueue().getStreamKey(),
//                            Map.of("batch", json)
//                    );
                 } catch (Exception e) {
                    log.error("Failed to requeue message", e);
                 }
              });
   }

   private String getStackTrace(Throwable error) {
      StringWriter sw = new StringWriter();
      error.printStackTrace(new PrintWriter(sw));
      return sw.toString();
   }

   private void sendAlert(String subject, Map<String, Object> details) {
      log.warn("ALERT: {} - {}", subject, details);
      // TODO: Integrate with Slack, PagerDuty, Email, etc.
   }

}
