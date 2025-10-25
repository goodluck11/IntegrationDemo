package com.demo.integration.it.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/*
 * @created by 24/10/2025  - 00:51
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class IdempotencyGuard {

   private final RedisTemplate<String, String> redisTemplate;

   private static final String IDEMPOTENCY_PREFIX = "processed:batch:";
   private static final Duration TTL = Duration.ofDays(7);

   public IdempotencyGuard(RedisTemplate<String, String> redisTemplate) {
      this.redisTemplate = redisTemplate;
   }

   public boolean markAsProcessed(String batchId) {
      String key = IDEMPOTENCY_PREFIX + batchId;

      Boolean success = redisTemplate.opsForValue()
              .setIfAbsent(key, Instant.now().toString(), TTL);

      if (Boolean.TRUE.equals(success)) {
         log.debug("Batch {} marked as processed (first time)", batchId);
         return true;
      } else {
         log.warn("Batch {} already processed (duplicate detected)", batchId);
         return false;
      }
   }

   public boolean wasAlreadyProcessed(String batchId) {
      String key = IDEMPOTENCY_PREFIX + batchId;
      return redisTemplate.hasKey(key);
   }

   public void clearProcessedFlag(String batchId) {
      String key = IDEMPOTENCY_PREFIX + batchId;
      redisTemplate.delete(key);
      log.info("Cleared processed flag for batch {}", batchId);
   }
}
