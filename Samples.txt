# FLUX HTTP Client - Comprehensive Guide

A production-ready HTTP client library with resilience patterns, observability, and advanced workflow capabilities for Java/Spring applications.

---

## Table of Contents

1. [Quick Start](#1-quick-start)
2. [Client Configuration](#2-client-configuration)
3. [Basic HTTP Operations](#3-basic-http-operations)
4. [Request Customization](#4-request-customization)
5. [Response Handling](#5-response-handling)
6. [Parallel Execution](#6-parallel-execution)
7. [Sequential Execution](#7-sequential-execution)
8. [FluxHttpWorkflow - Advanced Workflows](#8-fluxhttpworkflow---advanced-workflows)
9. [Error Handling & Custom Error Messages](#9-error-handling--custom-error-messages)
10. [Resilience Patterns](#10-resilience-patterns)
11. [Logging & Masking](#11-logging--masking)
12. [SSL/TLS Configuration](#12-ssltls-configuration)
13. [Client Pool for Different Timeouts](#13-client-pool-for-different-timeouts)
14. [Filters](#14-filters)
15. [Email Notifications](#15-email-notifications)
16. [Spring Boot Integration](#16-spring-boot-integration)
17. [Real-World Use Cases](#17-real-world-use-cases)

---

## 1. Quick Start

### Minimal Setup

```java
// Simple client with defaults
FluxHttpClient client = FluxHttpClientBuilder.builder()
    .baseUrl("https://api.example.com")
    .build();

// Make a GET request
User user = client.get()
    .uri("/users/1")
    .retrieveBody(User.class)
    .block();

// Make a POST request
FluxResponse<User> response = client.post()
    .uri("/users")
    .bodyValue(new CreateUserRequest("John", "john@example.com"))
    .retrieve(User.class)
    .block();

// Don't forget to shutdown when done
client.shutdown();
```

### Production Setup

```java
FluxHttpClient client = FluxHttpClientBuilder.builder("payment-service")
    .baseUrl("https://api.payment.com")
    .timeouts(TimeoutConfig.custom()
        .connection(Duration.ofSeconds(5))
        .read(Duration.ofSeconds(30))
        .write(Duration.ofSeconds(30))
        .response(Duration.ofSeconds(60)))
    .header("X-API-Version", "2.0")
    .addRequestFilter(Filters.bearerToken(() -> tokenService.getToken()))
    .resilience(ResilienceConfig.custom()
        .retry(RetryConfig.custom()
            .maxAttempts(3)
            .backoffStrategy(BackoffStrategy.EXPONENTIAL_WITH_JITTER))
        .circuitBreaker(CircuitBreakerConfig.custom()
            .failureRate(50)
            .openStateWait(Duration.ofSeconds(30))))
    .logging(LoggingConfig.custom()
        .logAll()
        .maskFields("cardNumber", "cvv", "pin", "password"))
    .build();
```

---

## 2. Client Configuration

### 2.1 Timeout Configuration

```java
TimeoutConfig timeouts = TimeoutConfig.custom()
    .connection(Duration.ofSeconds(5))    // Time to establish connection
    .read(Duration.ofSeconds(30))         // Time to read data from socket
    .write(Duration.ofSeconds(30))        // Time to write data to socket
    .response(Duration.ofSeconds(60));    // Total time for complete response

// Pre-defined profiles
TimeoutConfig.fast();      // 2s conn, 5s read, 10s response
TimeoutConfig.defaults();  // 5s conn, 30s read, 60s response
TimeoutConfig.slow();      // 10s conn, 2min read, 5min response
```

### 2.2 Default Headers

```java
FluxHttpClientBuilder.builder()
    .header("X-Client-Id", "my-service")
    .header("X-API-Version", "2.0")
    .headers(map -> {
        map.put("X-Tenant-Id", "tenant-123");
        map.put("X-Region", "us-east-1");
    })
    .build();
```

**Note:** Per-request headers override global headers with the same name.

---

## 3. Basic HTTP Operations

### 3.1 All HTTP Methods

```java
// GET
client.get().uri("/users").retrieve(UserList.class);

// POST
client.post().uri("/users").bodyValue(newUser).retrieve(User.class);

// PUT
client.put().uri("/users/1").bodyValue(updateUser).retrieve(User.class);

// PATCH
client.patch().uri("/users/1").bodyValue(partialUpdate).retrieve(User.class);

// DELETE
client.delete().uri("/users/1").retrieve(Void.class);

// HEAD
client.head().uri("/users/1").retrieve(Void.class);

// OPTIONS
client.options().uri("/users").retrieve(String.class);
```

### 3.2 URI Templates

```java
// Simple placeholder
client.get().uri("/users/{}", 123).retrieve(User.class);

// Multiple placeholders
client.get().uri("/users/{}/orders/{}", userId, orderId).retrieve(Order.class);

// Query parameters (manual)
client.get().uri("/users?page=" + page + "&size=" + size).retrieve(UserList.class);
```

### 3.3 Response Types

```java
// Full response with metadata
FluxResponse<User> response = client.get().uri("/users/1").retrieve(User.class).block();
User body = response.getBody();
HttpStatus status = response.getStatus();
HttpHeaders headers = response.getHeaders();
long durationMs = response.getDurationMs();
String requestId = response.getRequestId();

// Body only (simpler)
User user = client.get().uri("/users/1").retrieveBody(User.class).block();

// String response
String json = client.get().uri("/users/1").retrieve().block().getBody();

// Void response (for DELETE, etc.)
client.delete().uri("/users/1").retrieve(Void.class).block();

// Flux for streaming
Flux<User> users = client.get().uri("/users/stream").retrieveFlux(User.class);
```

---

## 4. Request Customization

### 4.1 Headers

```java
client.post()
    .uri("/payments")
    .header("X-Idempotency-Key", UUID.randomUUID().toString())
    .header("X-Request-Source", "mobile-app")
    .headers(h -> {
        h.add("X-Custom-Header", "value1");
        h.add("X-Another-Header", "value2");
    })
    .bodyValue(payment)
    .retrieve(PaymentResult.class);
```

### 4.2 Content Type

```java
// JSON (default)
client.post().uri("/api").bodyValue(obj).retrieve(Result.class);

// Form data
client.post()
    .uri("/login")
    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
    .bodyValue("username=john&password=secret")
    .retrieve(LoginResult.class);

// XML
client.post()
    .uri("/soap")
    .contentType(MediaType.APPLICATION_XML)
    .bodyValue(xmlString)
    .retrieve(String.class);
```

### 4.3 Per-Request Overrides

```java
// Override response timeout for slow endpoint
client.post()
    .uri("/reports/generate")
    .bodyValue(reportRequest)
    .override(o -> o
        .responseTimeout(Duration.ofMinutes(5))  // Longer timeout
        .retryEnabled(false)                      // Disable retry
        .circuitBreakerEnabled(false))            // Disable circuit breaker
    .retrieve(Report.class);
```

---

## 5. Response Handling

### 5.1 Blocking vs Reactive

```java
// Blocking (for traditional code)
User user = client.get().uri("/users/1").retrieveBody(User.class).block();

// Blocking with timeout
User user = client.get().uri("/users/1").retrieveBody(User.class)
    .block(Duration.ofSeconds(30));

// Reactive (for WebFlux)
Mono<User> userMono = client.get().uri("/users/1").retrieveBody(User.class);

// Subscribe
client.get().uri("/users/1").retrieveBody(User.class)
    .subscribe(
        user -> log.info("Got user: {}", user),
        error -> log.error("Failed: {}", error.getMessage())
    );
```

### 5.2 Response Metadata

```java
FluxResponse<User> response = client.get().uri("/users/1").retrieve(User.class).block();

// Check status
if (response.getStatus().is2xxSuccessful()) {
    User user = response.getBody();
}

// Access headers
String etag = response.getHeaders().getFirst("ETag");
String contentType = response.getHeaders().getFirst("Content-Type");

// Timing info
log.info("Request {} took {}ms", response.getRequestId(), response.getDurationMs());
```

---

## 6. Parallel Execution

Execute multiple requests concurrently for better performance.

### 6.1 Basic Parallel

```java
// Execute 3 requests in parallel, wait for all
FluxParallelResult<Object> result = client.parallel(
    client.get().uri("/users/1").retrieve(User.class),
    client.get().uri("/orders/recent").retrieve(OrderList.class),
    client.get().uri("/notifications").retrieve(NotificationList.class)
).waitAll().execute().block();

// Access results by index
User user = (User) result.getResponses().get(0).getBody();
OrderList orders = (OrderList) result.getResponses().get(1).getBody();
NotificationList notifications = (NotificationList) result.getResponses().get(2).getBody();

log.info("Total time: {}ms", result.getTotalDurationMs());
```

### 6.2 Parallel Strategies

```java
// WAIT_ALL: Wait for all requests to complete (default)
// All requests run regardless of individual failures
client.parallel(req1, req2, req3)
    .waitAll()
    .execute();

// FAIL_FAST: Stop immediately on first failure
// Remaining requests are cancelled
client.parallel(req1, req2, req3)
    .failFast()
    .execute();

// PARTIAL_SUCCESS: Continue even if some fail
// Collect both successes and failures
FluxParallelResult<User> result = client.parallel(req1, req2, req3)
    .partialSuccess()
    .execute()
    .block();

// Check for partial failures
if (result.hasErrors()) {
    result.getErrors().forEach((index, error) -> 
        log.warn("Request {} failed: {}", index, error.getMessage()));
}

// Process successful responses
result.getResponses().forEach(response -> 
    processUser(response.getBody()));
```

### 6.3 Typed Parallel Results

```java
// When all requests return the same type
FluxParallelResult<User> result = client.parallel(
    client.get().uri("/users/1").retrieve(User.class),
    client.get().uri("/users/2").retrieve(User.class),
    client.get().uri("/users/3").retrieve(User.class)
).waitAll().execute().block();

List<User> users = result.getResponses().stream()
    .map(FluxResponse::getBody)
    .collect(Collectors.toList());
```

---

## 7. Sequential Execution

Execute requests in sequence, passing data between steps.

### 7.1 Basic Sequence

```java
// Step 1 result feeds into Step 2
Order order = client.sequence()
    .then(() -> client.get().uri("/users/1").retrieve(User.class))
    .then((User user) -> client.post()
        .uri("/orders")
        .bodyValue(new CreateOrderRequest(user.getId(), items))
        .retrieve(Order.class))
    .<Order>execute()
    .block();
```

### 7.2 Multi-Step Sequence

```java
// Authenticate → Get User → Create Order → Send Notification
String notificationId = client.sequence()
    // Step 1: Authenticate
    .then(() -> client.post()
        .uri("/auth/token")
        .bodyValue(credentials)
        .retrieve(AuthToken.class))
    // Step 2: Get user profile using token
    .then((AuthToken token) -> client.get()
        .uri("/users/me")
        .header("Authorization", "Bearer " + token.getAccessToken())
        .retrieve(User.class))
    // Step 3: Create order for user
    .then((User user) -> client.post()
        .uri("/orders")
        .bodyValue(new CreateOrderRequest(user.getId(), items))
        .retrieve(Order.class))
    // Step 4: Send notification
    .then((Order order) -> client.post()
        .uri("/notifications")
        .bodyValue(new NotificationRequest(order.getId(), "Order created"))
        .retrieve(NotificationResult.class))
    .<NotificationResult>execute()
    .map(NotificationResult::getId)
    .block();
```

---

## 8. FluxHttpWorkflow - Advanced Workflows

The most powerful feature for complex multi-step operations with error handling, rollback, and fire-and-forget capabilities.

### 8.1 Workflow Methods Overview

| Method | Description | Blocking? | Waits? |
|--------|-------------|-----------|--------|
| `step()` | HTTP call, sequential | No | Yes |
| `stepMono()` | Any Mono (DB, etc.) | No | Yes |
| `stepSync()` | Blocking call (JPA/JDBC) | Yes | Yes |
| `stepContinueOnError()` | Continue even if fails | No | Yes |
| `fireAndForget()` | HTTP calls in background | No | No |
| `fireAndForgetMono()` | Monos in background | No | No |
| `fireAndForgetSync()` | Blocking calls in background | Yes | No |
| `fireAndForgetWith()` | Background with result access | No | No |
| `onFailure()` | Error handler (HTTP) | No | - |
| `onFailureSync()` | Error handler (blocking) | Yes | - |

### 8.2 Basic Workflow

```java
PaymentResult result = FluxHttpWorkflow.start(client)
    // Step 1: Validate account
    .step(c -> c.post()
        .uri("/accounts/validate")
        .bodyValue(new ValidateRequest(accountId))
        .retrieve(ValidationResult.class))
    // Step 2: Process payment (uses Step 1 result)
    .step((c, validation) -> c.post()
        .uri("/payments/process")
        .bodyValue(new PaymentRequest(validation.getAccountId(), amount))
        .retrieve(PaymentResult.class))
    .execute();  // Blocks and returns result
```

### 8.3 Workflow with Database Integration

```java
// Using reactive database (R2DBC)
OrderResult result = FluxHttpWorkflow.start(client)
    // Step 1: Save to DB (reactive)
    .stepMono(() -> orderRepository.save(order))
    // Step 2: Call external API
    .step((c, savedOrder) -> c.post()
        .uri("/fulfillment/process")
        .bodyValue(new FulfillmentRequest(savedOrder.getId()))
        .retrieve(FulfillmentResult.class))
    // Step 3: Update DB with result (reactive)
    .stepMono(fulfillment -> orderRepository.updateStatus(
        fulfillment.getOrderId(), "FULFILLED"))
    .execute();

// Using blocking database (JPA/JDBC)
OrderResult result = FluxHttpWorkflow.start(client)
    // Step 1: Save to DB (blocking - runs on boundedElastic)
    .stepSync(() -> {
        Order saved = orderRepository.save(order);
        return saved;
    })
    // Step 2: Call external API
    .step((c, savedOrder) -> c.post()
        .uri("/fulfillment/process")
        .bodyValue(new FulfillmentRequest(savedOrder.getId()))
        .retrieve(FulfillmentResult.class))
    // Step 3: Update DB (blocking)
    .stepSync(fulfillment -> {
        orderRepository.updateStatus(fulfillment.getOrderId(), "FULFILLED");
        return fulfillment;
    })
    .execute();
```

### 8.4 Fire-and-Forget (Background Tasks)

```java
PaymentResult result = FluxHttpWorkflow.start(client)
    // Main flow: Process payment
    .step(c -> c.post()
        .uri("/payments/process")
        .bodyValue(paymentRequest)
        .retrieve(PaymentResult.class))
    
    // Fire-and-forget: These run in background after success
    // Don't block the response, don't fail the workflow if they fail
    .fireAndForget(
        () -> client.post().uri("/audit/log").bodyValue(auditEntry).retrieve(Void.class),
        () -> client.post().uri("/analytics/track").bodyValue(event).retrieve(Void.class)
    )
    
    // Fire-and-forget with access to result
    .fireAndForgetWith(payment -> 
        client.post()
            .uri("/notifications/send")
            .bodyValue(new NotificationRequest(payment.getId(), "Payment successful"))
            .retrieve(Void.class))
    
    // Fire-and-forget DB operation (blocking, runs on separate thread)
    .fireAndForgetSync(() -> {
        auditRepository.save(new AuditLog("payment_processed", paymentId));
    })
    
    // Fire-and-forget reactive DB
    .fireAndForgetMono(() -> 
        analyticsRepository.incrementCounter("payments_processed"))
    
    .execute();

// Result returned immediately after payment processed
// Background tasks continue running
```

### 8.5 Error Handling with Rollback

```java
PaymentResult result = FluxHttpWorkflow.start(client)
    // Step 1: Reserve funds
    .step(c -> c.post()
        .uri("/accounts/reserve")
        .bodyValue(new ReserveRequest(accountId, amount))
        .retrieve(ReservationResult.class))
    
    // Step 2: Process payment
    .step((c, reservation) -> c.post()
        .uri("/payments/execute")
        .bodyValue(new ExecuteRequest(reservation.getId()))
        .retrieve(PaymentResult.class))
    
    // On ANY failure: Release the reserved funds
    .onFailure(() -> client.post()
        .uri("/accounts/release")
        .bodyValue(new ReleaseRequest(accountId))
        .retrieve(Void.class))
    
    .execute();
```

### 8.6 Step Continue On Error (Non-Critical Steps)

```java
TransferResult result = FluxHttpWorkflow.start(client)
    // Step 1: Main transfer (critical)
    .step(c -> c.post()
        .uri("/transfers/execute")
        .bodyValue(transferRequest)
        .retrieve(TransferResult.class))
    
    // Step 2: Secondary notification (non-critical)
    // If this fails, workflow continues with previous result
    .stepContinueOnError(
        (c, transfer) -> c.post()
            .uri("/partners/notify")
            .bodyValue(new PartnerNotification(transfer.getId()))
            .retrieve(NotificationResult.class),
        (error, transfer) -> {
            log.warn("Partner notification failed: {}", error.getMessage());
            // Could save to retry queue here
            return transfer;  // Return previous result to continue
        }
    )
    
    // Step 3: Update status (continues even if Step 2 failed)
    .step((c, transfer) -> c.post()
        .uri("/transfers/complete")
        .bodyValue(new CompleteRequest(transfer.getId()))
        .retrieve(TransferResult.class))
    
    .execute();
```

### 8.7 Complex Real-World Workflow: Card Payment with Reversal

```java
public PostingResult processCardPayment(CardPaymentRequest request) {
    return FluxHttpWorkflow.start(fluxClient)
        // Step 1: Check card status
        .step(c -> c.get()
            .uri("/cards/{}/status", request.getCardId())
            .retrieve(CardStatus.class))
        
        // Step 2: Validate card is active
        .step((c, status) -> {
            if (!status.isActive()) {
                throw new CardInactiveException(request.getCardId());
            }
            return c.post()
                .uri("/cards/{}/details", request.getCardId())
                .bodyValue(new CardDetailsRequest(request.getPin()))
                .retrieve(CardDetails.class);
        })
        
        // Step 3: Check balance
        .step((c, card) -> c.post()
            .uri("/accounts/{}/balance", card.getAccountId())
            .bodyValue(new BalanceCheckRequest(request.getAmount()))
            .retrieve(BalanceResult.class))
        
        // Step 4: Execute posting
        .step((c, balance) -> {
            if (balance.getAvailable().compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(balance.getAvailable());
            }
            return c.post()
                .uri("/postings/execute")
                .bodyValue(new PostingRequest(
                    balance.getAccountId(),
                    request.getAmount(),
                    request.getMerchant()))
                .retrieve(PostingResult.class);
        })
        
        // Fire-and-forget: Audit and notifications
        .fireAndForgetWith(posting -> 
            client.post()
                .uri("/audit/transactions")
                .bodyValue(new AuditEntry("CARD_PAYMENT", posting.getId()))
                .retrieve(Void.class))
        
        .fireAndForgetWith(posting ->
            client.post()
                .uri("/notifications/sms")
                .bodyValue(new SmsRequest(request.getPhone(), 
                    "Payment of " + request.getAmount() + " processed"))
                .retrieve(Void.class))
        
        // On failure: Reverse any partial posting
        .onFailure(error -> client.post()
            .uri("/postings/reverse")
            .bodyValue(new ReversalRequest(request.getTransactionId(), error.getMessage()))
            .retrieve(Void.class))
        
        .execute(Duration.ofSeconds(30));  // With timeout
}
```

### 8.8 Async Workflow Execution

```java
// Non-blocking execution - returns Mono
Mono<PaymentResult> resultMono = FluxHttpWorkflow.start(client)
    .step(c -> c.post().uri("/payments").bodyValue(request).retrieve(PaymentResult.class))
    .executeAsync();

// Subscribe to result
resultMono.subscribe(
    result -> log.info("Payment completed: {}", result.getId()),
    error -> log.error("Payment failed: {}", error.getMessage())
);

// Fire completely in background (no result tracking)
FluxHttpWorkflow.start(client)
    .step(c -> c.post().uri("/background/job").bodyValue(job).retrieve(Void.class))
    .fireAndForget(
        () -> client.post().uri("/cleanup").retrieve(Void.class)
    )
    .executeAndForget();  // Returns immediately, runs in background
```

---

## 9. Error Handling & Custom Error Messages

### 9.1 Global Error Mappers

Define custom exceptions based on HTTP status codes:

```java
FluxHttpClient client = FluxHttpClientBuilder.builder()
    .baseUrl("https://api.example.com")
    
    // Map 404 to custom exception
    .addErrorMapper(ErrorMapper.onStatus(
        status -> status == HttpStatus.NOT_FOUND,
        ctx -> new ResourceNotFoundException(
            "Resource not found: " + ctx.getUri(),
            ctx.getRequestId())))
    
    // Map 401/403 to auth exception
    .addErrorMapper(ErrorMapper.onStatus(
        status -> status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN,
        ctx -> new AuthenticationException(
            "Authentication failed for " + ctx.getUri(),
            ctx.getStatusCode().value())))
    
    // Map 5xx to service exception with response body
    .addErrorMapper(ErrorMapper.onStatus(
        HttpStatus::is5xxServerError,
        ctx -> new ServiceUnavailableException(
            "Service error: " + ctx.getBody(),
            ctx.getStatusCode().value(),
            ctx.getRequestId())))
    
    // Map specific status code
    .addErrorMapper(ErrorMapper.onStatus(
        status -> status.value() == 429,
        ctx -> new RateLimitException(
            "Rate limit exceeded",
            ctx.getHeaders().getFirst("Retry-After"))))
    
    .build();
```

### 9.2 Per-Request Error Mappers

```java
// Override error handling for specific request
User user = client.get()
    .uri("/users/{}", userId)
    .onStatus(
        status -> status == HttpStatus.NOT_FOUND,
        ctx -> new UserNotFoundException("User " + userId + " not found"))
    .onStatus(
        status -> status == HttpStatus.FORBIDDEN,
        ctx -> new AccessDeniedException("Cannot access user " + userId))
    .retrieveBody(User.class)
    .block();
```

### 9.3 Error Context

```java
.addErrorMapper(ErrorMapper.onStatus(
    HttpStatus::is4xxClientError,
    ctx -> {
        // Available context
        String requestId = ctx.getRequestId();      // Unique request ID
        HttpStatus status = ctx.getStatusCode();    // HTTP status
        String method = ctx.getMethod();            // GET, POST, etc.
        String uri = ctx.getUri();                  // Request URI
        String body = ctx.getBody();                // Response body
        HttpHeaders headers = ctx.getHeaders();     // Response headers
        
        // Parse error response
        ErrorResponse error = parseError(ctx.getBody());
        
        return new ApiException(
            error.getMessage(),
            error.getCode(),
            requestId,
            status.value());
    }))
```

### 9.4 Custom Exception Classes

```java
// Base exception with request context
public class ApiException extends RuntimeException {
    private final String errorCode;
    private final String requestId;
    private final int httpStatus;
    
    public ApiException(String message, String errorCode, String requestId, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = requestId;
        this.httpStatus = httpStatus;
    }
    
    // Convert to API response
    public ErrorResponse toErrorResponse() {
        return new ErrorResponse(
            errorCode,
            getMessage(),
            requestId,
            Instant.now()
        );
    }
}

// Specific exceptions
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resource, String requestId) {
        super("Resource not found: " + resource, "NOT_FOUND", requestId, 404);
    }
}

public class InsufficientFundsException extends ApiException {
    private final BigDecimal available;
    
    public InsufficientFundsException(BigDecimal available) {
        super("Insufficient funds. Available: " + available, "INSUFFICIENT_FUNDS", null, 400);
        this.available = available;
    }
}
```

### 9.5 Handling Errors in Parallel Requests

```java
// With PARTIAL_SUCCESS strategy
FluxParallelResult<User> result = client.parallel(
    client.get().uri("/users/1").retrieve(User.class),
    client.get().uri("/users/999").retrieve(User.class),  // Might not exist
    client.get().uri("/users/3").retrieve(User.class)
)
.partialSuccess()
.execute()
.block();

// Process results and errors
for (int i = 0; i < 3; i++) {
    if (result.getErrors().containsKey(i)) {
        Throwable error = result.getErrors().get(i);
        log.warn("Request {} failed: {}", i, error.getMessage());
        
        // Custom handling per error type
        if (error instanceof ResourceNotFoundException) {
            // Handle not found
        } else if (error instanceof ServiceUnavailableException) {
            // Maybe retry later
        }
    } else {
        User user = result.getResponses().get(i).getBody();
        processUser(user);
    }
}
```

### 9.6 Handling Errors in Workflows

```java
// Workflow with comprehensive error handling
try {
    PaymentResult result = FluxHttpWorkflow.start(client)
        .step(c -> c.post()
            .uri("/payments")
            .bodyValue(request)
            .retrieve(PaymentResult.class))
        .onFailure(error -> client.post()
            .uri("/payments/rollback")
            .bodyValue(new RollbackRequest(request.getId()))
            .retrieve(Void.class))
        .execute();
        
    return ApiResponse.success(result);
    
} catch (InsufficientFundsException e) {
    return ApiResponse.error("INSUFFICIENT_FUNDS", 
        "Not enough balance. Available: " + e.getAvailable());
        
} catch (CardInactiveException e) {
    return ApiResponse.error("CARD_INACTIVE", 
        "Card is not active: " + e.getCardId());
        
} catch (FluxHttpClientException e) {
    log.error("Payment failed - requestId: {}, uri: {}", 
        e.getRequestId(), e.getUri(), e);
    return ApiResponse.error("PAYMENT_FAILED", 
        "Payment processing failed. Reference: " + e.getRequestId());
        
} catch (Exception e) {
    log.error("Unexpected error", e);
    return ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred");
}
```

### 9.7 Controller Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FluxHttpClientException.class)
    public ResponseEntity<ErrorResponse> handleFluxException(FluxHttpClientException e) {
        log.error("Flux client error - requestId: {}, method: {}, uri: {}", 
            e.getRequestId(), e.getMethod(), e.getUri(), e);
        
        ErrorResponse response = new ErrorResponse(
            "SERVICE_ERROR",
            "External service call failed",
            e.getRequestId(),
            Instant.now()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }
    
    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreaker(CircuitBreakerOpenException e) {
        ErrorResponse response = new ErrorResponse(
            "SERVICE_UNAVAILABLE",
            "Service temporarily unavailable. Please retry later.",
            e.getRequestId(),
            Instant.now()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "30")
            .body(response);
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException e) {
        ErrorResponse response = new ErrorResponse(
            "RATE_LIMITED",
            "Too many requests. Please slow down.",
            e.getRequestId(),
            Instant.now()
        );
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "60")
            .body(response);
    }
}
```

---

## 10. Resilience Patterns

### 10.1 Retry Configuration

```java
ResilienceConfig.custom()
    .retry(RetryConfig.custom()
        .enabled(true)
        .maxAttempts(3)                                    // Total attempts
        .waitDuration(Duration.ofMillis(500))              // Initial wait
        .backoffStrategy(BackoffStrategy.EXPONENTIAL_WITH_JITTER)
        .backoffMultiplier(2.0)                            // 500ms → 1s → 2s
        .retryOnConnectionTimeout(true)
        .retryOnReadTimeout(true)
        .retryExceptions(IOException.class, TimeoutException.class)
        .ignoreExceptions(BadRequestException.class))      // Don't retry 4xx
```

### 10.2 Circuit Breaker

```java
ResilienceConfig.custom()
    .circuitBreaker(CircuitBreakerConfig.custom()
        .enabled(true)
        .failureRate(50)                                   // Open at 50% failures
        .slowCallRate(80)                                  // Open at 80% slow calls
        .slowCallDuration(Duration.ofSeconds(5))           // Define "slow"
        .windowSize(10)                                    // Sliding window
        .minCalls(5)                                       // Min calls before evaluating
        .openStateWait(Duration.ofSeconds(30))             // Time in OPEN state
        .halfOpenCalls(3)                                  // Calls in HALF_OPEN
        .autoTransition(true))                             // Auto transition to HALF_OPEN
```

### 10.3 Rate Limiter

```java
ResilienceConfig.custom()
    .rateLimiter(RateLimiterConfig.custom()
        .enabled(true)
        .limitForPeriod(100)                               // 100 calls
        .refreshPeriod(Duration.ofSeconds(1))              // Per second
        .timeout(Duration.ofMillis(500)))                  // Wait timeout
```

### 10.4 Bulkhead (Concurrency Limit)

```java
ResilienceConfig.custom()
    .bulkhead(BulkheadConfig.custom()
        .enabled(true)
        .maxConcurrentCalls(25)                            // Max parallel calls
        .maxWait(Duration.ofMillis(100)))                  // Wait for slot
```

### 10.5 Disable Resilience Per-Request

```java
// For specific requests that shouldn't be retried
client.post()
    .uri("/payments/charge")  // Idempotency issues
    .bodyValue(chargeRequest)
    .override(o -> o
        .retryEnabled(false)
        .circuitBreakerEnabled(false))
    .retrieve(ChargeResult.class);
```

---

## 11. Logging & Masking

### 11.1 Logging Configuration

```java
LoggingConfig.custom()
    .logAll()                              // Log requests, responses, headers
    // OR selective:
    .logRequest()                          // Only requests
    .logResponse()                         // Only responses
    .logHeaders()                          // Include headers
    
    .maxBodySize(10_000)                   // Truncate bodies > 10KB
```

### 11.2 Field Masking

```java
LoggingConfig.custom()
    .logAll()
    
    // Add fields to mask (case-insensitive, partial match)
    .maskFields("password", "cardNumber", "cvv", "pin", "ssn", "iban")
    
    // Custom visible characters per field
    .maskField("cardNumber", 4)            // Show first/last 4: "4111********1111"
    .maskField("cvv", 0)                   // Fully masked: "********"
    
    // Exclude fields entirely (not logged at all)
    .excludeFields("internalId", "debugData")
    
    // Customize mask appearance
    .maskSymbol('X')                       // Use X instead of *
    .defaultVisibleChars(4)                // Default visible chars
```

### 11.3 Log Output Example

```
╔══════════════════════════════════════════════════════════════════╗
║ FLUX HTTP REQUEST
╠══════════════════════════════════════════════════════════════════╣
║ Request ID: req-a1b2c3d4
║ Method: POST
║ URI: https://api.example.com/payments
║ Headers:
║   Content-Type: application/json
║   Authorization: Bear********oken
║   X-Request-Id: req-a1b2c3d4
║ Body:
{
  "amount" : 100.00,
  "cardNumber" : "4111********1111",
  "cvv" : "********",
  "accountIbanDetails" : "QA58********3456"
}
╚══════════════════════════════════════════════════════════════════╝
```

---

## 12. SSL/TLS Configuration

### 12.1 Custom Certificates

```java
SslConfig.custom()
    .enabled(true)
    .certificatePath("/path/to/cert.pem")          // Server certificate
    .keyPath("/path/to/key.pem")                   // Private key
    .keyPassword("keypass")                         // Key password
    .trustStorePath("/path/to/truststore.jks")     // Trust store
    .trustStorePassword("trustpass")
    .handshakeTimeout(Duration.ofSeconds(10))
```

### 12.2 Disable SSL Verification (Development Only!)

```java
SslConfig.custom()
    .enabled(true)
    .trustAll(true)  // ⚠️ NEVER use in production!
```

---

## 13. Client Pool for Different Timeouts

When you need different timeout profiles for different types of requests:

```java
// Create a pool with shared configuration
FluxHttpClientPool pool = FluxHttpClientPool.builder()
    .baseUrl("https://api.example.com")
    .resilience(ResilienceConfig.custom()
        .retry(RetryConfig.custom().maxAttempts(3)))
    .logging(LoggingConfig.custom().logAll())
    .addRequestFilter(Filters.bearerToken(() -> tokenService.getToken()))
    .build();

// Use pre-defined profiles
FluxHttpClient fastClient = pool.getFast();      // 2s conn, 5s read, 10s response
FluxHttpClient defaultClient = pool.getDefault(); // 5s conn, 30s read, 60s response
FluxHttpClient slowClient = pool.getSlow();      // 10s conn, 2min read, 5min response
FluxHttpClient batchClient = pool.getBatch();    // 10s conn, 10min read, 30min response

// Or create custom profiles
FluxHttpClient reportClient = pool.getOrCreate("reports", 
    TimeoutProfile.builder()
        .connection(Duration.ofSeconds(10))
        .read(Duration.ofMinutes(5))
        .response(Duration.ofMinutes(10))
        .build());

// Usage
User user = fastClient.get().uri("/users/1").retrieveBody(User.class).block();
Report report = reportClient.get().uri("/reports/annual").retrieveBody(Report.class).block();

// Shutdown all clients
pool.shutdown();
```

---

## 14. Filters

### 14.1 Built-in Request Filters

```java
// Bearer token (static)
Filters.bearerToken("my-token")

// Bearer token (dynamic - refreshed on each request)
Filters.bearerToken(() -> tokenService.getAccessToken())

// Basic authentication
Filters.basicAuth("username", "password")

// API key header
Filters.apiKey("X-API-Key", "my-api-key")

// Custom headers
Filters.headers("X-Client", "my-app", "X-Version", "1.0")

// Timestamp header
Filters.timestamp("X-Request-Time")

// Audit logging
Filters.auditLog("my-service")
```

### 14.2 Custom Request Filter

```java
// Add correlation ID from MDC
RequestFilter correlationFilter = context -> {
    String correlationId = MDC.get("correlationId");
    if (correlationId != null) {
        context.addHeader("X-Correlation-Id", correlationId);
    }
    return Mono.just(context);
};

// Conditional header
RequestFilter conditionalFilter = context -> {
    if (context.getUri().contains("/admin")) {
        context.addHeader("X-Admin-Token", adminToken);
    }
    return Mono.just(context);
};

// Request validation
RequestFilter validationFilter = context -> {
    if (context.getBody() == null && context.getMethod() == HttpMethod.POST) {
        return Mono.error(new IllegalArgumentException("POST requires body"));
    }
    return Mono.just(context);
};

client = FluxHttpClientBuilder.builder()
    .addRequestFilter(correlationFilter)
    .addRequestFilter(conditionalFilter)
    .addRequestFilter(validationFilter)
    .build();
```

### 14.3 Built-in Response Filters

```java
// Response audit logging
Filters.responseAuditLog("my-service")

// Slow response warning
Filters.slowResponseLog(5000)  // Warn if > 5 seconds

// Error logging
Filters.errorLog()

// Validate success
Filters.validateSuccess()  // Throws on non-2xx
```

### 14.4 Custom Response Filter

```java
ResponseFilter metricsFilter = context -> {
    metrics.recordLatency(context.getUri(), context.getDurationMs());
    metrics.recordStatus(context.getUri(), context.getStatusCode());
    return Mono.just(context);
};

ResponseFilter headerExtractor = context -> {
    String rateLimit = context.getHeaders().getFirst("X-RateLimit-Remaining");
    if (rateLimit != null && Integer.parseInt(rateLimit) < 10) {
        log.warn("Rate limit nearly exhausted: {}", rateLimit);
    }
    return Mono.just(context);
};
```

---

## 15. Email Notifications

### 15.1 Configuration

```java
EmailNotifierConfig.custom()
    .host("smtp.company.com")
    .port(587)
    .username("alerts@company.com")
    .password("smtp-password")
    .useTls(true)
    .defaultFrom("alerts@company.com")
    .defaultTo("ops-team@company.com")
```

### 15.2 Per-Request Notifications

```java
client.post()
    .uri("/critical/operation")
    .bodyValue(request)
    .onErrorNotify(mail -> mail
        .subject("Critical Operation Failed")
        .to("urgent@company.com")
        .cc("manager@company.com"))
    .retrieve(Result.class);
```

---

## 16. Spring Boot Integration

### 16.1 Auto-Configuration

```yaml
# application.yml
flux:
  rest-client:
    default:
      base-url: https://api.example.com
      timeouts:
        connection: 5s
        read: 30s
        response: 60s
      resilience:
        retry:
          enabled: true
          max-attempts: 3
        circuit-breaker:
          enabled: true
          failure-rate: 50
      logging:
        enabled: true
        log-request: true
        log-response: true
        mask-fields: password,token,cardNumber
```

### 16.2 Bean Configuration

```java
@Configuration
public class FluxClientConfig {

    @Bean
    @PreDestroy
    public FluxHttpClient paymentClient(
            JavaMailSender mailSender,
            TokenService tokenService) {
        return FluxHttpClientBuilder.builder("payment-client")
            .baseUrl("https://api.payment.com")
            .withSpringMailSender(mailSender)
            .addRequestFilter(Filters.bearerToken(tokenService::getToken))
            .build();
    }
    
    @Bean(destroyMethod = "shutdown")
    public FluxHttpClientPool clientPool() {
        return FluxHttpClientPool.builder()
            .baseUrl("https://api.example.com")
            .build();
    }
}
```

### 16.3 Lifecycle Management

```java
@Component
public class FluxClientManager implements DisposableBean {
    
    private final FluxHttpClient client;
    
    public FluxClientManager() {
        this.client = FluxHttpClientBuilder.builder().build();
    }
    
    @Override
    public void destroy() {
        client.shutdown();  // Clean shutdown on app stop
    }
}

// Or with @PreDestroy
@Component
public class MyService {
    private final FluxHttpClient client;
    
    @PreDestroy
    public void cleanup() {
        client.shutdown();
    }
}
```

---

## 17. Real-World Use Cases

### 17.1 Payment Processing Service

```java
@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final FluxHttpClient fluxClient;
    private final PaymentRepository paymentRepository;
    
    public PaymentResponse processPayment(PaymentRequest request) {
        try {
            // Complex workflow with multiple steps
            PostingResult result = FluxHttpWorkflow.start(fluxClient)
                // Step 1: Fraud check
                .step(c -> c.post()
                    .uri("/fraud/check")
                    .bodyValue(new FraudCheckRequest(request))
                    .retrieve(FraudCheckResult.class))
                
                // Step 2: Validate with bank
                .step((c, fraudResult) -> {
                    if (fraudResult.getRiskScore() > 80) {
                        throw new FraudDetectedException(fraudResult.getReason());
                    }
                    return c.post()
                        .uri("/bank/validate")
                        .bodyValue(request)
                        .retrieve(BankValidation.class);
                })
                
                // Step 3: Execute payment
                .step((c, validation) -> c.post()
                    .uri("/payments/execute")
                    .bodyValue(new ExecuteRequest(validation.getToken(), request.getAmount()))
                    .retrieve(PostingResult.class))
                
                // Background: Audit & notifications
                .fireAndForgetWith(posting -> fluxClient.post()
                    .uri("/audit/log")
                    .bodyValue(new AuditEntry("PAYMENT", posting.getId()))
                    .retrieve(Void.class))
                
                .fireAndForgetSync(() -> 
                    paymentRepository.save(Payment.fromRequest(request)))
                
                // Rollback on failure
                .onFailure(error -> fluxClient.post()
                    .uri("/payments/rollback")
                    .bodyValue(new RollbackRequest(request.getTransactionId()))
                    .retrieve(Void.class))
                
                .execute(Duration.ofSeconds(30));
            
            return PaymentResponse.success(result);
            
        } catch (FraudDetectedException e) {
            return PaymentResponse.declined("FRAUD_DETECTED", e.getMessage());
        } catch (InsufficientFundsException e) {
            return PaymentResponse.declined("INSUFFICIENT_FUNDS", 
                "Available: " + e.getAvailable());
        } catch (Exception e) {
            log.error("Payment failed", e);
            return PaymentResponse.error("PROCESSING_ERROR", 
                "Please try again later");
        }
    }
}
```

### 17.2 Dashboard Aggregation

```java
@Service
public class DashboardService {
    
    private final FluxHttpClient client;
    
    public DashboardData getDashboard(String userId) {
        // Fetch all data in parallel
        FluxParallelResult<Object> result = client.parallel(
            client.get().uri("/users/{}", userId).retrieve(User.class),
            client.get().uri("/users/{}/orders/recent", userId).retrieve(OrderList.class),
            client.get().uri("/users/{}/notifications", userId).retrieve(NotificationList.class),
            client.get().uri("/users/{}/recommendations", userId).retrieve(RecommendationList.class),
            client.get().uri("/promotions/active").retrieve(PromotionList.class)
        )
        .partialSuccess()  // Don't fail if recommendations are down
        .execute()
        .block();
        
        // Build dashboard with available data
        DashboardData dashboard = new DashboardData();
        dashboard.setUser((User) getOrNull(result, 0));
        dashboard.setRecentOrders((OrderList) getOrNull(result, 1));
        dashboard.setNotifications((NotificationList) getOrNull(result, 2));
        dashboard.setRecommendations((RecommendationList) getOrNull(result, 3));
        dashboard.setPromotions((PromotionList) getOrNull(result, 4));
        
        // Log any failures
        result.getErrors().forEach((idx, error) -> 
            log.warn("Dashboard component {} failed: {}", idx, error.getMessage()));
        
        return dashboard;
    }
    
    private Object getOrNull(FluxParallelResult<?> result, int index) {
        if (result.getErrors().containsKey(index)) {
            return null;
        }
        return result.getResponses().get(index).getBody();
    }
}
```

### 17.3 Batch Processing with Rate Limiting

```java
@Service
public class BatchProcessor {
    
    private final FluxHttpClient client;
    
    public BatchResult processBatch(List<Item> items) {
        List<ProcessingResult> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        
        // Process in chunks to respect rate limits
        List<List<Item>> chunks = Lists.partition(items, 10);
        
        for (List<Item> chunk : chunks) {
            // Process chunk in parallel
            List<Mono<FluxResponse<ProcessingResult>>> requests = chunk.stream()
                .map(item -> client.post()
                    .uri("/process")
                    .bodyValue(item)
                    .retrieve(ProcessingResult.class))
                .collect(Collectors.toList());
            
            FluxParallelResult<ProcessingResult> chunkResult = client
                .parallel(requests.toArray(new Mono[0]))
                .partialSuccess()
                .execute()
                .block();
            
            // Collect results
            for (int i = 0; i < chunk.size(); i++) {
                if (chunkResult.getErrors().containsKey(i)) {
                    failures.add(chunk.get(i).getId() + ": " + 
                        chunkResult.getErrors().get(i).getMessage());
                } else {
                    results.add(chunkResult.getResponses().get(i).getBody());
                }
            }
            
            // Rate limit between chunks
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return new BatchResult(results, failures);
    }
}
```

### 17.4 Event-Driven with Fire-and-Forget

```java
@Service
public class OrderService {
    
    private final FluxHttpClient client;
    private final OrderRepository orderRepository;
    
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // Main operation: Create order
        Order order = FluxHttpWorkflow.start(client)
            .step(c -> c.post()
                .uri("/orders")
                .bodyValue(request)
                .retrieve(Order.class))
            
            // Fire-and-forget: Trigger all downstream systems
            // These don't block the response
            .fireAndForgetWith(o -> client.post()
                .uri("/inventory/reserve")
                .bodyValue(new ReserveRequest(o.getItems()))
                .retrieve(Void.class))
            
            .fireAndForgetWith(o -> client.post()
                .uri("/shipping/schedule")
                .bodyValue(new ShippingRequest(o.getId(), o.getAddress()))
                .retrieve(Void.class))
            
            .fireAndForgetWith(o -> client.post()
                .uri("/email/send")
                .bodyValue(new EmailRequest(o.getCustomerEmail(), "Order Confirmed", 
                    "Your order " + o.getId() + " has been placed"))
                .retrieve(Void.class))
            
            .fireAndForgetWith(o -> client.post()
                .uri("/analytics/track")
                .bodyValue(new AnalyticsEvent("order_created", o.getId()))
                .retrieve(Void.class))
            
            // Background DB save
            .fireAndForgetSync(() -> 
                orderRepository.save(OrderEntity.fromOrder(order)))
            
            // Custom error handler for fire-and-forget failures
            .onFireAndForgetError(error -> 
                log.warn("Background task failed: {}", error.getMessage()))
            
            .execute();
        
        return order;  // Returns immediately after order created
    }
}
```

---

## Summary

The FLUX REST Client provides a comprehensive solution for HTTP communication in Java applications with:

- **Fluent API** for readable, chainable requests
- **Multiple execution patterns**: standalone, parallel, sequential, workflow
- **Production-ready resilience**: retry, circuit breaker, rate limiter, bulkhead
- **Comprehensive logging** with sensitive data masking
- **Flexible error handling** with custom exception mapping
- **Fire-and-forget** for non-blocking background tasks
- **Database integration** for both reactive and blocking operations
- **Spring Boot integration** with auto-configuration

For questions or issues, contact the platform team.
