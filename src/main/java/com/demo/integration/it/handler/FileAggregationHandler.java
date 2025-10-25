package com.demo.integration.it.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import java.io.File;

/*
 * @created by 24/10/2025  - 15:02
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class FileAggregationHandler {

   private final FileMovementHandler fileMovementHandler;

   public FileAggregationHandler(FileMovementHandler fileMovementHandler) {
      this.fileMovementHandler = fileMovementHandler;
   }

   @Aggregator
   public String aggregateBatches(MessageGroup msgGroup) {
      if (msgGroup.size() == 0) return "No batches processed";

      MessageHeaders headers = msgGroup.getOne().getHeaders();
      File originalFile = (File) headers.get(FileHeaders.ORIGINAL_FILE);
      String fileName = (String) headers.get("fileName");
      Integer expectedSize = (Integer) headers.get(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE);

      boolean isComplete = (expectedSize != null && msgGroup.size() == expectedSize);
      if (!isComplete) {
         log.warn("Incomplete batch set for file: {}. Expected: {}, Received: {}",
                 fileName, expectedSize, msgGroup.size());
      }

      log.info("Aggregated {} batches for file: {} (complete: {})",
              msgGroup.size(), fileName, isComplete);

      if (originalFile != null) {
         try {
            if (isComplete) {
               fileMovementHandler.moveToProcessed(originalFile);
               return "File queued: %s".formatted(originalFile.getName());
            } else {
               log.error("One or more batches failed for file: {}", originalFile.getName());
               fileMovementHandler.moveToError(originalFile);
               return String.format("File incomplete: %s (%d/%d batches)",
                       originalFile.getName(), msgGroup.size(), expectedSize);
            }
         } catch (Exception e) {
            log.error("Failed to move file: {}", originalFile.getName(), e);
            return "File movement failed: " + originalFile.getName();
         }
      }

      log.warn("Original file not found in headers for: {}", fileName);
      return "File queued: " + (fileName != null ? fileName : "unknown");
   }
}
