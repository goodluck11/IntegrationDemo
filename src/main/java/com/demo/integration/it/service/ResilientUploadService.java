package com.demo.integration.it.service;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.exception.TokenExpiredException;
import com.demo.integration.it.model.BatchRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/*
 * @created by 24/10/2025  - 00:51
 * @project IntegrationDemo
 * @author Goodluck
 */
@Service
@Slf4j
public class ResilientUploadService {

   private final WebClient webClient;
   private final AuthenticationService authService;
   private final FileProcessorProperties properties;
   private final CircuitBreaker circuitBreaker;
   private final Retry retry;
   private final RateLimiter rateLimiter;

   public ResilientUploadService(WebClient uploadWebClient,
                                 AuthenticationService authService,
                                 FileProcessorProperties properties,
                                 CircuitBreaker batchUploadCircuitBreaker,
                                 Retry batchUploadRetry,
                                 RateLimiter batchUploadRateLimiter) {
      this.webClient = uploadWebClient;
      this.authService = authService;
      this.properties = properties;
      this.circuitBreaker = batchUploadCircuitBreaker;
      this.retry = batchUploadRetry;
      this.rateLimiter = batchUploadRateLimiter;
   }

   public Mono<String> uploadBatch(BatchRequest batch) {
      return Mono.fromCallable(authService::getAccessToken)
              .flatMap(token ->
                      webClient.post()
                              .uri(properties.getUploadUrl())
                              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                              .bodyValue(batch)
                              .retrieve()
                              .onStatus(
                                      status -> status.value() == 401,
                                      response -> Mono.error(new TokenExpiredException("Token rejected by server"))
                              )
                              .bodyToMono(String.class)
                              .doOnSuccess(response ->
                                      log.info("Successfully uploaded batch: {}", batch.getBatchId()))
                              .doOnError(error ->
                                      log.error("Failed to upload batch: {}", batch.getBatchId(), error))
              )
              .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
              .transformDeferred(RetryOperator.of(retry))
              .transformDeferred(RateLimiterOperator.of(rateLimiter))
              .onErrorResume(error -> {
                 log.error("All retries exhausted for batch: {}", batch.getBatchId(), error);
                 return Mono.error(error);
              });
   }
}
