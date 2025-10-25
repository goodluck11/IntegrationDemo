package com.demo.integration.it.exception;

/*
 * @created by 24/10/2025  - 00:54
 * @project IntegrationDemo
 * @author Goodluck
 */
public class AuthenticationException extends RuntimeException {

   public AuthenticationException(String message) {
      super(message);
   }

   public AuthenticationException(String message, Throwable cause) {
      super(message, cause);
   }
}
