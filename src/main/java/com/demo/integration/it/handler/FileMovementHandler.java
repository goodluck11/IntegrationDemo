package com.demo.integration.it.handler;

import com.demo.integration.it.config.FileProcessorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/*
 * @created by 24/10/2025  - 00:50
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class FileMovementHandler {
   private final FileProcessorProperties properties;

   public FileMovementHandler(FileProcessorProperties properties) {
      this.properties = properties;
   }

   public void moveToProcessed(File file) {
      try {
         Path source = file.toPath();
         Path target = Paths.get(properties.getProcessedPath(), file.getName());
         Files.createDirectories(target.getParent());
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
         log.info("Moved to processed: {}", file.getName());
      } catch (IOException e) {
         log.error("Failed to move file to processed", e);
      }
   }

   public void moveToError(File file) {
      try {
         Path source = file.toPath();
         Path target = Paths.get(properties.getErrorPath(), file.getName());
         Files.createDirectories(target.getParent());
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
         log.error("Moved to error: {}", file.getName());
      } catch (IOException e) {
         log.error("Failed to move file to error", e);
      }
   }
}
