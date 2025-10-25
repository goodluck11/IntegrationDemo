package com.demo.integration.it.service;

import com.demo.integration.it.exception.ExcelParsingException;
import com.demo.integration.it.model.OrderRecord;
import lombok.extern.slf4j.Slf4j;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/*
 * @created by 24/10/2025  - 13:14
 * @project IntegrationDemo
 * @author Goodluck
 */

@Service
@Slf4j
public class FastExcelReaderService {
   private final ExcelReaderService excelReaderService;

   public FastExcelReaderService(ExcelReaderService excelReaderService) {
      this.excelReaderService = excelReaderService;
   }

   public List<OrderRecord> readExcelFile(File file) throws ExcelParsingException {
      List<OrderRecord> records = new ArrayList<>();

      try (FileInputStream fis = new FileInputStream(file);
           ReadableWorkbook workbook = new ReadableWorkbook(fis)) {

         try (Stream<Row> rows = workbook.getFirstSheet().openStream()) {
            rows.skip(1).forEach(row -> {
               try {
                  var orderRecord = OrderRecord.builder()
                          .orderId(row.getCellAsString(0).orElse(""))
                          .customerName(row.getCellAsString(1).orElse(""))
                          .product(row.getCellAsString(2).orElse(""))
                          .amount(row.getCellAsNumber(3).orElse(null))
                          .orderDate(row.getCellAsDate(4).map(java.time.LocalDateTime::toLocalDate).orElse(null))
                          .build();
                  records.add(orderRecord);
               } catch (Exception e) {
                  log.warn("Skipping invalid row {} in file {}: {}",
                          row.getRowNum(), file.getName(), e.getMessage());
               }
            });
         }
         log.info("Successfully read {} records from file: {}", records.size(), file.getName());
         return records;
      } catch (IOException e) {
         log.error("Error reading Excel file with FastExcelReader: {}", file.getName(), e);
      }

      return excelReaderService.readExcelFile(file);
   }
}
