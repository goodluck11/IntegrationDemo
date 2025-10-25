package com.demo.integration.it.config;

/*
 * @created by 24/10/2025  - 00:48
 * @project IntegrationDemo
 * @author Goodluck
 */

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "file-processor")
@Data
public class FileProcessorProperties {
   private String inboxPath;
   private String processedPath;
   private String errorPath;
   private int batchSize;
   private int maxRetries;
   private long retryDelayMs;
   private long maxRetryDelayMs;
   private long retryLockTimeoutMs;
   private int retryBatchSize;

   private String alertErrorCodes = "500,503,401,403";

   private String authUrl;
   private String uploadUrl;

   private String username;
   private String password;

   private RedisQueue redisQueue = new RedisQueue();
   private HttpClient httpClient = new HttpClient();

   @Data
   public static class RedisQueue {
      private String streamKey = "batch-upload-stream";
      private String consumerGroup = "upload-workers";
      private int batchSize = 10;
      private long pollTimeout = 5000;
      private String dlqKey = "batch-upload-dlq";
   }

   @Data
   public static class HttpClient {
      private int maxConnections = 50;
      private int connectionTimeout = 5000;
      private int readTimeout = 30000;
      private int concurrentUploads = 10;
   }
}
