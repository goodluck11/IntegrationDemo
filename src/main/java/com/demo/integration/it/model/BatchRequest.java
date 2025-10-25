package com.demo.integration.it.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/*
 * @created by 24/10/2025  - 00:53
 * @project IntegrationDemo
 * @author Goodluck
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequest {
   private String batchId;
   private String requestId;

   @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
   private Instant timestamp;

   private String sourceFileName;
   private List<OrderRecord> records;
}
