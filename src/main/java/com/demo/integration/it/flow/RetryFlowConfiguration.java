package com.demo.integration.it.flow;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.model.BatchRequest;
import com.demo.integration.it.model.FailedBatch;
import com.demo.integration.it.service.FailedBatchService;
import com.demo.integration.it.service.ResilientUploadService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.ReactiveMessageHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/*
 * @created by 24/10/2025  - 00:49
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
@Slf4j
public class RetryFlowConfiguration {

   private final FileProcessorProperties properties;
   private final FailedBatchService failedBatchService;
   private final ResilientUploadService uploadService;
   private final ObjectMapper objectMapper;

   public RetryFlowConfiguration(FileProcessorProperties properties,
                                 FailedBatchService failedBatchService,
                                 ResilientUploadService uploadService,
                                 ObjectMapper objectMapper) {
      this.properties = properties;
      this.failedBatchService = failedBatchService;
      this.uploadService = uploadService;
      this.objectMapper = objectMapper;
   }

   @Bean
   public IntegrationFlow retryFailedBatchesFlow() {
      return IntegrationFlow
              .from(() -> null, e -> e.poller(
                      Pollers.fixedDelay(randomizedDelay())
                              .maxMessagesPerPoll(1)
              ))
              .handle(new ReactiveMessageHandlerAdapter(message ->
                      Mono.fromCallable(() ->
                                      failedBatchService.lockAndGetBatchesForRetry(properties.getRetryBatchSize()))
                              .subscribeOn(Schedulers.boundedElastic())
                              .doOnNext(batches ->
                                      log.info("Found {} batches ready for retry", batches.size()))
                              .flatMapMany(Flux::fromIterable)
                              .flatMap(failedBatch ->
                                      processFailedBatch(failedBatch)
                                              .doOnError(e -> log.warn("Batch {} retry failed", failedBatch.getBatchId(), e))
                                              .onErrorResume(e -> Mono.empty()))
                              .then()

              ))
              .get();
   }

   private Mono<Void> processFailedBatch(FailedBatch failedBatch) {
      long startTime = System.currentTimeMillis();

      log.info("Retrying batch: {} (attempt {}/{})",
              failedBatch.getBatchId(),
              failedBatch.getRetryCount() + 1,
              failedBatch.getMaxRetries());

      return Mono.fromRunnable(() -> failedBatchService.markRetrying(failedBatch))
              .subscribeOn(Schedulers.boundedElastic())
              .then(Mono.defer(() -> {
                 BatchRequest batch;
                 try {
                    batch = objectMapper.readValue(failedBatch.getPayload(), BatchRequest.class);
                 } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize batch: {}", failedBatch.getBatchId(), e);
                    return Mono.fromRunnable(() ->
                            failedBatchService.markFailed(failedBatch, e.getMessage(), "DESERIALIZATION_ERROR")
                    ).subscribeOn(Schedulers.boundedElastic()).then();
                 }

                 return uploadService.uploadBatch(batch)
                         .doOnSuccess(response -> {
                            log.info("Successfully uploaded batch: {}", failedBatch.getBatchId());
                         })
                         .flatMap(response -> Mono.fromRunnable(() ->
                                 failedBatchService.markSuccess(failedBatch)
                         ).subscribeOn(Schedulers.boundedElastic()))
                         .doOnError(error -> log.error("Failed to upload batch: {}", failedBatch.getBatchId(), error))
                         .onErrorResume(error -> Mono.fromRunnable(() ->
                                 handleUploadError(failedBatch, error)
                         ).subscribeOn(Schedulers.boundedElastic()).then())
                         .doFinally(signalType -> {
                            long duration = System.currentTimeMillis() - startTime;
                            log.info("Batch {} processing finished [{}] after {} ms",
                                    failedBatch.getBatchId(), signalType, duration);
                         })
                         .then();
              }));
   }

   private void handleUploadError(FailedBatch failedBatch, Throwable error) {
      String errorCode = extractErrorCode(error);
      String errorMessage = error.getMessage();

      if (isNonRetryableError(error)) {
         log.warn("Non-retryable error for batch {}: {}", failedBatch.getBatchId(), errorMessage);
         failedBatchService.markFailed(failedBatch, errorMessage, errorCode);
         return;
      }

      if (failedBatch.getRetryCount() + 1 >= failedBatch.getMaxRetries()) {
         log.warn("Batch {} reached max retries", failedBatch.getBatchId());
         failedBatchService.markFailed(failedBatch, errorMessage, errorCode);
         return;
      }

      log.info("Unlocking batch {} for next retry", failedBatch.getBatchId());
      failedBatchService.unlockBatch(failedBatch);
   }

   private String extractErrorCode(Throwable error) {
      if (error instanceof WebClientResponseException webError) {
         return String.valueOf(webError.getStatusCode().value());
      }

      if (error instanceof CallNotPermittedException) {
         return "CIRCUIT_BREAKER_OPEN";
      }

      if (error instanceof RequestNotPermitted) {
         return "RATE_LIMIT_EXCEEDED";
      }

      return "GENERIC_ERROR";
   }

   private boolean isNonRetryableError(Throwable error) {
      if (error instanceof WebClientResponseException webError) {
         HttpStatusCode status = webError.getStatusCode();
         return status.is4xxClientError() && status.value() != 429;
      }
      return false;
   }

   private Duration randomizedDelay() {
      int baseSeconds = 30;
      int jitter = ThreadLocalRandom.current().nextInt(0, 11);
      int totalDelay = baseSeconds + jitter;
      return Duration.ofSeconds(totalDelay);
   }
}