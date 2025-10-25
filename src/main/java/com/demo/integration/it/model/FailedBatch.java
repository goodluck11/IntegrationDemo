package com.demo.integration.it.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/*
 * @created by 24/10/2025  - 00:53
 * @project IntegrationDemo
 * @author Goodluck
 */
@Entity
@Table(name = "failed_batches")
@Data
public class FailedBatch {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false)
   private String batchId;

   @Column(nullable = false)
   private String requestId;

   private String fileName;

   @Column(columnDefinition = "TEXT", nullable = false)
   private String payload;

   @Column(columnDefinition = "TEXT")
   private String errorMessage;

   @Column(name = "retry_count", nullable = false)
   private Integer retryCount = 0;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private BatchStatus status;

   @Column(name = "max_retries", nullable = false)
   private Integer maxRetries;

   @Column
   private String errorCode;

   @Column(name = "created_at", nullable = false)
   private Instant createdAt;

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt;

   @Column(name = "next_retry_at")
   private Instant nextRetryAt;

   @Column(name = "locked_by")
   private String lockedBy;

   @Column(name = "locked_at")
   private Instant lockedAt;

   @Version
   private Long version;

   @PrePersist
   protected void onCreate() {
      if (createdAt == null) {
         createdAt = Instant.now();
      }
      if (updatedAt == null) {
         updatedAt = Instant.now();
      }
   }

   public enum BatchStatus {
      PENDING,
      RETRYING,
      SUCCESS,
      FAILED,
      LOCKED
   }
}
