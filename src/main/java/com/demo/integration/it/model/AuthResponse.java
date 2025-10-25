package com.demo.integration.it.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;

/*
 * @created by 24/10/2025  - 00:53
 * @project IntegrationDemo
 * @author Goodluck
 */
@Data
public class AuthResponse {
   private String token;

   @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
   private Instant expiresAt;
}
