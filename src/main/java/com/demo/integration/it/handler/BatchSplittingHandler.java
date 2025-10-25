package com.demo.integration.it.handler;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.model.OrderRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.Splitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/*
 * @created by 24/10/2025  - 00:50
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class BatchSplittingHandler {

   private final FileProcessorProperties properties;

   public BatchSplittingHandler(FileProcessorProperties properties) {
      this.properties = properties;
   }

   @Splitter
   public List<List<OrderRecord>> splitIntoBatches(List<OrderRecord> records) {
      List<List<OrderRecord>> batches = new ArrayList<>();
      int batchSize = properties.getBatchSize();

      for (int i = 0; i < records.size(); i += batchSize) {
         int end = Math.min(i + batchSize, records.size());
         List<OrderRecord> batch = new ArrayList<>(records.subList(i, end));
         batches.add(batch);
      }

      log.info("Split {} records into {} batches of size {}",
              records.size(), batches.size(), batchSize);
      return batches;
   }
}
