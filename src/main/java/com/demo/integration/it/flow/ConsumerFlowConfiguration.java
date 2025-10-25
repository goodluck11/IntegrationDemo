package com.demo.integration.it.flow;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.handler.HttpUploadHandler;
import com.demo.integration.it.model.BatchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.redis.inbound.ReactiveRedisStreamMessageProducer;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

/*
 * @created by 24/10/2025  - 00:49
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
@Slf4j
public class ConsumerFlowConfiguration {

   private final FileProcessorProperties properties;
   private final HttpUploadHandler httpUploadHandler;
   private final ObjectMapper objectMapper;

   public ConsumerFlowConfiguration(FileProcessorProperties properties,
                                    HttpUploadHandler httpUploadHandler,
                                    ObjectMapper objectMapper) {
      this.properties = properties;
      this.httpUploadHandler = httpUploadHandler;
      this.objectMapper = objectMapper;
   }

   @Bean
   public ReactiveRedisStreamMessageProducer batchStreamProducer(
           LettuceConnectionFactory redisConnectionFactory) {
      var messageProducer =
              new ReactiveRedisStreamMessageProducer(redisConnectionFactory, properties.getRedisQueue().getStreamKey());
      messageProducer.setStreamReceiverOptions(
              StreamReceiver.StreamReceiverOptions.builder()
                      .pollTimeout(Duration.ofMillis(properties.getRedisQueue().getPollTimeout()))
                      .targetType(String.class)
                      .build());
      messageProducer.setAutoStartup(true);
      messageProducer.setAutoAck(false);
      messageProducer.setCreateConsumerGroup(true);
      messageProducer.setConsumerGroup(properties.getRedisQueue().getConsumerGroup());
      messageProducer.setConsumerName("worker-%s".formatted(UUID.randomUUID()));
      messageProducer.setReadOffset(ReadOffset.latest());
      messageProducer.setExtractPayload(true);
      return messageProducer;
   }

   @Bean
   public IntegrationFlow redisQueueToHttpFlow(
           LettuceConnectionFactory redisConnectionFactory,
           TaskExecutor httpUploadExecutor
   ) {
      return IntegrationFlow
              .from(batchStreamProducer(redisConnectionFactory))
              .wireTap(flow -> flow
                      .handle(message -> log.info("Received raw message from Redis: headers={}, payload={}",
                              message.getHeaders(), message.getPayload())))
              .transform(String.class, json -> {
                 try {
                    return objectMapper.readValue(json, BatchRequest.class);
                 } catch (Exception e) {
                    log.error("Failed to parse batch from queue", e);
                    throw new MessagingException("JSON parsing failed", e);
                 }
              })
              .channel(MessageChannels.executor(httpUploadExecutor))
              .handle(httpUploadHandler)
              .get();
   }

   @Bean
   public TaskExecutor httpUploadExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(properties.getHttpClient().getConcurrentUploads());
      executor.setMaxPoolSize(properties.getHttpClient().getConcurrentUploads() * 2);
      executor.setQueueCapacity(100);
      executor.setThreadNamePrefix("http-upload-");
      executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
      executor.initialize();
      return executor;
   }
}