package com.demo.integration.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;

@SpringBootApplication
@EnableIntegration
public class IntegrationDemoApplication {

   public static void main(String[] args) {
      SpringApplication.run(IntegrationDemoApplication.class, args);
   }

}
