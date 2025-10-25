package com.demo.integration.it.config;

import com.demo.integration.it.exception.AuthenticationException;
import com.demo.integration.it.exception.TokenExpiredException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/*
 * @created by 24/10/2025  - 00:48
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
public class Resilience4jConfiguration {

   @Bean
   public CircuitBreaker batchUploadCircuitBreaker() {
      CircuitBreakerConfig config = CircuitBreakerConfig.custom()
              .slidingWindowSize(100)
              .minimumNumberOfCalls(10)
              .permittedNumberOfCallsInHalfOpenState(5)
              .waitDurationInOpenState(Duration.ofSeconds(30))
              .failureRateThreshold(50)
              .slowCallRateThreshold(50)
              .slowCallDurationThreshold(Duration.ofSeconds(10))
              .automaticTransitionFromOpenToHalfOpenEnabled(true)
              .build();

      return CircuitBreaker.of("batchUpload", config);
   }

   @Bean
   public Retry batchUploadRetry() {
      RetryConfig config = RetryConfig.custom()
              .maxAttempts(3)
              .waitDuration(Duration.ofSeconds(2))
              .retryExceptions(
                      WebClientRequestException.class,
                      TimeoutException.class,
                      AuthenticationException.class,
                      TokenExpiredException.class)
              .build();

      return Retry.of("batchUpload", config);
   }

   @Bean
   public RateLimiter batchUploadRateLimiter() {
      RateLimiterConfig config = RateLimiterConfig.custom()
              .limitForPeriod(100)
              .limitRefreshPeriod(Duration.ofSeconds(1))
              .timeoutDuration(Duration.ZERO)
              .build();

      return RateLimiter.of("batchUpload", config);
   }
}
