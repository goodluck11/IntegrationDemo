package com.demo.integration.it.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/*
 * @created by 24/10/2025  - 00:54
 * @project IntegrationDemo
 * @author Goodluck
 */

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ExcelParsingException extends RuntimeException {

   public ExcelParsingException(String message) {
      super(message);
   }

   public ExcelParsingException(String message, Throwable cause) {
      super(message, cause);
   }
}
