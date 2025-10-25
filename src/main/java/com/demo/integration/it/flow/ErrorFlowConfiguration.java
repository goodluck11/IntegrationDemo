package com.demo.integration.it.flow;

import com.demo.integration.it.constant.AppConstants;
import com.demo.integration.it.handler.FileMovementHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.redis.metadata.RedisMetadataStore;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;

import java.io.File;

/*
 * @created by 24/10/2025  - 13:01
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
@Slf4j
public class ErrorFlowConfiguration {
   private final FileMovementHandler fileMovementHandler;
   private final RedisMetadataStore redisMetadataStore;

   public ErrorFlowConfiguration(FileMovementHandler fileMovementHandler, RedisMetadataStore redisMetadataStore) {
      this.fileMovementHandler = fileMovementHandler;
      this.redisMetadataStore = redisMetadataStore;
   }

   @Bean
   public IntegrationFlow fileProcessingErrorFlow() {
      return IntegrationFlow
              .from(AppConstants.ERROR_CHANNEL)
              .handle(message -> {
                 ErrorMessage errorMessage = (ErrorMessage) message;
                 MessagingException exception = (MessagingException) errorMessage.getPayload();

                 Message<?> failedMessage = exception.getFailedMessage();
                 if (failedMessage != null) {
                    File originalFile = (File) failedMessage.getHeaders().get(FileHeaders.ORIGINAL_FILE);
                    if (originalFile != null) {
                       log.info("Original file path: {}", originalFile.getAbsolutePath());
                       log.error("Error processing file: {}", originalFile.getName(), exception);
                       fileMovementHandler.moveToError(originalFile);
                    }
                 }
              })
              .get();
   }

   private void removeFromSeenFiles(File file) {
      try {
         redisMetadataStore.remove("fileTracker:" + file.getAbsolutePath());
         log.info("Removed {} from tracking - can be retried", file.getName());
      } catch (Exception e) {
         log.error("Failed to remove file from tracking", e);
      }
   }
}
