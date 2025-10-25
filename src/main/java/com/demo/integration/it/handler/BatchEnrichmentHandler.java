package com.demo.integration.it.handler;

import com.demo.integration.it.model.BatchRequest;
import com.demo.integration.it.model.OrderRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * @created by 24/10/2025  - 00:50
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class BatchEnrichmentHandler {

   @Transformer
   public BatchRequest createBatchRequest(
           @Payload List<OrderRecord> records,
           @Header("fileName") String fileName,
           @Header(value = "sequenceNumber", required = false) Integer sequenceNumber) {

      BatchRequest batch = new BatchRequest();
      batch.setBatchId(UUID.randomUUID().toString());
      batch.setRequestId(UUID.randomUUID().toString());
      batch.setTimestamp(Instant.now());
      batch.setSourceFileName(fileName);
      batch.setRecords(records);

      log.info("Created batch {} from file {} with {} records (sequence: {})",
              batch.getBatchId(), fileName, records.size(), sequenceNumber);

      return batch;
   }
}