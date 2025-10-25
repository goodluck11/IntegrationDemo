package com.demo.integration.it.service;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.exception.AuthenticationException;
import com.demo.integration.it.model.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/*
 * @created by 24/10/2025  - 00:51
 * @project IntegrationDemo
 * @author Goodluck
 */
@Service
@Slf4j
public class AuthenticationService {

   private final WebClient webClient;
   private final FileProcessorProperties properties;
   private final StringRedisTemplate redisTemplate;
   private final ObjectMapper objectMapper;
   private final LockRegistry redisLockRegistry;

   private static final String TOKEN_CACHE_KEY = "api:auth:token";
   private static final String TOKEN_REFRESH_LOCK = "api:auth:refresh:lock";
   private static final String TOKEN_METADATA_KEY = "api:auth:metadata";
   private static final Duration TOKEN_BUFFER = Duration.ofMinutes(3);
   private static final Duration MIN_TOKEN_LIFETIME = Duration.ofMinutes(2);
   private static final long LOCK_WAIT_TIMEOUT_MS = 10000;

   public AuthenticationService(WebClient webClient,
                                FileProcessorProperties properties,
                                StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                LockRegistry redisLockRegistry) {
      this.webClient = webClient;
      this.properties = properties;
      this.redisTemplate = redisTemplate;
      this.objectMapper = objectMapper;
      this.redisLockRegistry = redisLockRegistry;
   }

   public String getAccessToken() {
      String cachedToken = getCachedToken();
      if (cachedToken != null) {
         log.debug("Using cached token");
         return cachedToken;
      }

      return refreshTokenWithLock();
   }

   private String getCachedToken() {
      try {
         String token = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
         if (token == null) {
            return null;
         }

         String metadataJson = redisTemplate.opsForValue().get(TOKEN_METADATA_KEY);
         if (metadataJson != null) {
            TokenMetadata metadata = objectMapper.readValue(metadataJson, TokenMetadata.class);
            long minExpiryTime = Instant.now().plus(TOKEN_BUFFER).toEpochMilli();

            if (metadata.getExpiresAt() > minExpiryTime) {
               log.debug("Token is valid (expires in {}s)",
                       Duration.ofMillis(metadata.getExpiresAt() - System.currentTimeMillis()).getSeconds());
               return token;
            } else {
               log.debug("Token is expiring soon ({}s remaining), will refresh",
                       Duration.ofMillis(metadata.getExpiresAt() - System.currentTimeMillis()).getSeconds());
            }
         }

         return null;
      } catch (Exception e) {
         log.warn("Error checking cached token, will refresh", e);
         return null;
      }
   }

   private String refreshTokenWithLock() {
      Lock lock = redisLockRegistry.obtain(TOKEN_REFRESH_LOCK);

      try {
         if (lock.tryLock(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            try {
               String token = getCachedToken();
               if (token != null) {
                  log.info("Token already refreshed by another worker");
                  return token;
               }

               return fetchAndCacheNewToken();
            } finally {
               lock.unlock();
               log.debug("Released distributed lock");
            }
         } else {
            log.warn("Failed to acquire lock within {}ms, checking if token was refreshed", LOCK_WAIT_TIMEOUT_MS);

            String token = getCachedToken();
            if (token != null) {
               log.info("Token refreshed by another worker while waiting");
               return token;
            }

            throw new AuthenticationException("Failed to acquire lock and no valid token available after waiting");
         }

      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new AuthenticationException("Interrupted while waiting for lock", e);
      }
   }

   private String fetchAndCacheNewToken() {
      Map<String, String> authRequest = Map.of(
              "username", properties.getUsername(),
              "password", properties.getPassword()
      );

      try {
         AuthResponse authResponse = webClient.post()
                 .contentType(MediaType.APPLICATION_JSON)
                 .bodyValue(authRequest)
                 .retrieve()
                 .onStatus(
                         status -> status.is4xxClientError() || status.is5xxServerError(),
                         clientResponse -> clientResponse.bodyToMono(String.class)
                                 .flatMap(errorBody -> {
                                    log.error("Authentication failed with status {}: {}",
                                            clientResponse.statusCode(), errorBody);
                                    return Mono.error(new AuthenticationException(
                                            "Authentication failed with status: " + clientResponse.statusCode()));
                                 })
                 )
                 .bodyToMono(AuthResponse.class)
                 .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                         .maxBackoff(Duration.ofSeconds(5))
                         .filter(throwable -> !(throwable instanceof AuthenticationException))
                         .doBeforeRetry(retrySignal ->
                                 log.warn("Retrying authentication (attempt {})", retrySignal.totalRetries() + 1))
                 )
                 .timeout(Duration.ofSeconds(15))
                 .block();

         if (authResponse == null || authResponse.getToken() == null) {
            throw new AuthenticationException("Invalid auth response");
         }

         Instant expiresAt = authResponse.getExpiresAt();
         if (expiresAt == null) {
            expiresAt = Instant.now().plus(Duration.ofMinutes(10));
         }

         Duration ttl = Duration.between(Instant.now(), expiresAt).minus(TOKEN_BUFFER);
         if (ttl.compareTo(MIN_TOKEN_LIFETIME) < 0) {
            ttl = MIN_TOKEN_LIFETIME;
         }

         TokenMetadata metadata = TokenMetadata
                 .builder()
                 .expiresAt(expiresAt.toEpochMilli())
                 .refreshedAt(Instant.now().toEpochMilli())
                 .build();

         redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, authResponse.getToken(), ttl);
         redisTemplate.opsForValue().set(TOKEN_METADATA_KEY, objectMapper.writeValueAsString(metadata), ttl);

         log.info("Successfully obtained and cached new token (TTL: {}s)", ttl.getSeconds());
         return authResponse.getToken();

      } catch (Exception e) {
         log.error("Authentication failed", e);
         throw new AuthenticationException("Failed to authenticate", e);
      }
   }

   @Data
   @NoArgsConstructor
   @AllArgsConstructor
   @Builder
   public static class TokenMetadata {
      private long expiresAt;
      private long refreshedAt;
   }
}
