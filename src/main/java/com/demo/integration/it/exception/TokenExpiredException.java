package com.demo.integration.it.exception;

/*
 * @created by 24/10/2025  - 00:54
 * @project IntegrationDemo
 * @author Goodluck
 */
public class TokenExpiredException extends RuntimeException {

   public TokenExpiredException(String message) {
      super(message);
   }

   public TokenExpiredException(String message, Throwable cause) {
      super(message, cause);
   }
}
