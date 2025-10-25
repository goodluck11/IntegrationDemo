package com.demo.integration.it.service;

import com.demo.integration.it.exception.ExcelParsingException;
import com.demo.integration.it.model.OrderRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * @created by 24/10/2025  - 00:50
 * @project IntegrationDemo
 * @author Goodluck
 */
@Service
@Slf4j
public class ExcelReaderService {

   public List<OrderRecord> readExcelFile(File file) throws ExcelParsingException {
      List<OrderRecord> records = new ArrayList<>();

      try (FileInputStream fis = new FileInputStream(file);
           Workbook workbook = WorkbookFactory.create(fis)) {

         Sheet sheet = workbook.getSheetAt(0);

         for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            try {
               var orderRecord = OrderRecord.builder()
                       .orderId(getCellValueAsString(row.getCell(0)))
                       .customerName(getCellValueAsString(row.getCell(1)))
                       .product(getCellValueAsString(row.getCell(2)))
                       .amount(new BigDecimal(getCellValueAsString(row.getCell(3))))
                       .orderDate(getCellValueAsDate(row.getCell(4)))
                       .build();
               records.add(orderRecord);
            } catch (Exception e) {
               log.warn("Skipping invalid row {} in file {}: {}",
                       i, file.getName(), e.getMessage());
            }
         }

         log.info("Successfully read {} records from file: {}",
                 records.size(), file.getName());
         return records;

      } catch (Exception e) {
         log.error("Error reading Excel file: {}", file.getName(), e);
         throw new ExcelParsingException("Failed to parse Excel file: " + file.getName(), e);
      }
   }

   private String getCellValueAsString(Cell cell) {
      if (cell == null) return "";

      return switch (cell.getCellType()) {
         case STRING -> cell.getStringCellValue();
         case NUMERIC -> {
            if (DateUtil.isCellDateFormatted(cell)) {
               yield cell.getLocalDateTimeCellValue().toString();
            } else {
               yield String.valueOf(cell.getNumericCellValue());
            }
         }
         case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
         case FORMULA -> cell.getCellFormula();
         default -> "";
      };
   }

   private LocalDate getCellValueAsDate(Cell cell) {
      if (cell == null) return null;

      try {
         if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
         }
      } catch (Exception e) {
         log.warn("Failed to parse date from cell: {}", e.getMessage());
      }

      return null;
   }
}
