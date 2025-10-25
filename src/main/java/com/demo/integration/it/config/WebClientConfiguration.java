package com.demo.integration.it.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/*
 * @created by 24/10/2025  - 00:48
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
public class WebClientConfiguration {

   private final FileProcessorProperties properties;

   public WebClientConfiguration(FileProcessorProperties properties) {
      this.properties = properties;
   }

   @Bean
   public WebClient uploadWebClient() {
      HttpClient httpClient = HttpClient.create()
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                      properties.getHttpClient().getConnectionTimeout())
              .responseTimeout(Duration.ofMillis(
                      properties.getHttpClient().getReadTimeout()))
              .doOnConnected(conn ->
                      conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                              .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

      return WebClient.builder()
              .clientConnector(new ReactorClientHttpConnector(httpClient))
              .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .build();
   }
}
