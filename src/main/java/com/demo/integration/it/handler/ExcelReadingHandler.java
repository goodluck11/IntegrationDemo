package com.demo.integration.it.handler;

import com.demo.integration.it.exception.ExcelParsingException;
import com.demo.integration.it.model.OrderRecord;
import com.demo.integration.it.service.FastExcelReaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.file.FileHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/*
 * @created by 24/10/2025  - 00:49
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class ExcelReadingHandler {

   private final FastExcelReaderService excelReader;
   private final FileMovementHandler fileMovementHandler;

   public ExcelReadingHandler(FastExcelReaderService excelReader,
                              FileMovementHandler fileMovementHandler) {
      this.excelReader = excelReader;
      this.fileMovementHandler = fileMovementHandler;
   }

   @ServiceActivator
   public List<OrderRecord> readExcelFile(File file,
                                          @Header(value = FileHeaders.FILENAME, required = false) String filename,
                                          @Header(value = FileHeaders.ORIGINAL_FILE, required = false) String filePath,
                                          @Header("id") String id) {
      log.info("Processing  Excel file {}, corrId={}, filePath={}", filename, id, filePath);

      try {
         List<OrderRecord> records = excelReader.readExcelFile(file);

         if (records.isEmpty()) {
            throw new IllegalStateException("No records found in file: " + file.getName());
         }

         log.info("Successfully read {} records from {}", records.size(), file.getName());
         return records;

      } catch (Exception e) {
         log.error("Failed to read Excel file: {}", file.getName(), e);
         fileMovementHandler.moveToError(file);
         throw new ExcelParsingException("Excel parsing failed for " + file.getName(), e);
      }
   }
}
