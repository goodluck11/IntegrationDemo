package com.demo.integration.it.constant;

/*
 * @created by 24/10/2025  - 14:00
 * @project IntegrationDemo
 * @author Goodluck
 */
public class AppConstants {

   private AppConstants() {}

   // Channels
   public static  final String ERROR_CHANNEL = "fileProcessingErrorChannel";

   // Headers
   public static final String BATCH_FAILED_HEADER = "batchFailed";
   public static final String BATCH_ERROR_HEADER = "batchError";
   public static final String BATCH_PUBLISH_TIME_HEADER = "batchPublishTime";
}
