package com.demo.integration.it.guard;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/*
 * @created by 24/10/2025  - 00:52
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class FileIntegrityValidator {

   private final RedisTemplate<String, String> redisTemplate;

   private static final String CHECKSUM_PREFIX = "file:checksum:";
   private static final Duration CHECKSUM_TTL = Duration.ofDays(30);

   public FileIntegrityValidator(RedisTemplate<String, String> redisTemplate) {
      this.redisTemplate = redisTemplate;
   }

   public String calculateChecksum(File file) throws IOException, NoSuchAlgorithmException {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");

      try (FileInputStream fis = new FileInputStream(file)) {
         byte[] buffer = new byte[8192];
         int bytesRead;

         while ((bytesRead = fis.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
         }
      }

      byte[] hashBytes = digest.digest();
      return Base64.getEncoder().encodeToString(hashBytes);
   }

   @SneakyThrows
   public boolean validateAndStore(File file) {
      String checksum = calculateChecksum(file);
      String key = CHECKSUM_PREFIX + file.getName();

      String existingChecksum = redisTemplate.opsForValue().get(key);

      if (existingChecksum != null) {
         if (existingChecksum.equals(checksum)) {
            log.warn("File {} already processed (identical checksum)", file.getName());
            return false;
         } else {
            log.info("File {} has different content (checksum changed)", file.getName());
         }
      }

      redisTemplate.opsForValue().set(key, checksum, CHECKSUM_TTL);
      log.debug("Stored checksum for file {}", file.getName());

      return true;
   }
}
