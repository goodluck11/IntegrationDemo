package com.demo.integration.it.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/*
 * @created by 24/10/2025  - 00:52
 * @project IntegrationDemo
 * @author Goodluck
 */
@Component
@Slf4j
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {

   private final AtomicInteger inFlightMessages = new AtomicInteger(0);

   public void incrementInFlight() {
      inFlightMessages.incrementAndGet();
   }

   public void decrementInFlight() {
      inFlightMessages.decrementAndGet();
   }

   public int getInFlightCount() {
      return inFlightMessages.get();
   }

   @Override
   public void onApplicationEvent(ContextClosedEvent event) {
      log.info("Shutdown signal received. Waiting for {} in-flight messages...",
              inFlightMessages.get());

      long startTime = System.currentTimeMillis();
      long maxWaitMs = 30000;

      while (inFlightMessages.get() > 0) {
         long elapsed = System.currentTimeMillis() - startTime;

         if (elapsed > maxWaitMs) {
            log.warn("Shutdown timeout reached. {} messages still in-flight. Force shutdown.",
                    inFlightMessages.get());
            break;
         }

         try {
            Thread.sleep(500);
            log.debug("Waiting... {} messages remaining", inFlightMessages.get());
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted during graceful shutdown");
            break;
         }
      }

      log.info("Graceful shutdown complete. Elapsed: {}ms",
              System.currentTimeMillis() - startTime);
   }
}
