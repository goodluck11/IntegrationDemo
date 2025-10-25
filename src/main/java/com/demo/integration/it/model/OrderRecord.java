package com.demo.integration.it.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
 * @created by 24/10/2025  - 00:53
 * @project IntegrationDemo
 * @author Goodluck
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRecord {
   private String orderId;
   private String customerName;
   private String product;
   private BigDecimal amount;

   @JsonFormat(pattern = "yyyy-MM-dd")
   private LocalDate orderDate;
}
