package com.demo.integration.it.flow;

import com.demo.integration.it.config.FileProcessorProperties;
import com.demo.integration.it.constant.AppConstants;
import com.demo.integration.it.guard.FileIntegrityValidator;
import com.demo.integration.it.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.aggregator.SimpleSequenceSizeReleaseStrategy;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.FileSystemPersistentAcceptOnceFileListFilter;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.redis.metadata.RedisMetadataStore;
import org.springframework.integration.redis.store.RedisMessageStore;
import org.springframework.integration.transaction.TransactionInterceptorBuilder;
import org.springframework.transaction.annotation.Isolation;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

/*
 * @created by 24/10/2025  - 00:49
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
@Slf4j
public class ProducerFlowConfiguration {

   private final FileProcessorProperties properties;
   private final FileIntegrityValidator fileIntegrityValidator;
   private final ExcelReadingHandler excelReadingHandler;
   private final BatchSplittingHandler batchSplittingHandler;
   private final BatchEnrichmentHandler batchEnrichmentHandler;
   private final RedisBatchWriterHandler batchWriterHandler;
   private final FileAggregationHandler fileAggregationHandler;

   public ProducerFlowConfiguration(FileProcessorProperties properties,
                                    FileIntegrityValidator fileIntegrityValidator,
                                    ExcelReadingHandler excelReadingHandler,
                                    BatchSplittingHandler batchSplittingHandler,
                                    BatchEnrichmentHandler batchEnrichmentHandler,
                                    RedisBatchWriterHandler batchWriterHandler,
                                    FileAggregationHandler fileAggregationHandler) {
      this.properties = properties;
      this.fileIntegrityValidator = fileIntegrityValidator;
      this.excelReadingHandler = excelReadingHandler;
      this.batchSplittingHandler = batchSplittingHandler;
      this.batchEnrichmentHandler = batchEnrichmentHandler;
      this.batchWriterHandler = batchWriterHandler;
      this.fileAggregationHandler = fileAggregationHandler;
   }

   @Bean
   public IntegrationFlow fileToRedisQueueFlow(RedisMetadataStore redisMetadataStore,
                                               RedisMessageStore redisMessageStore) {
      return IntegrationFlow
              .from(Files.inboundAdapter(new File(properties.getInboxPath()))
                              .filter(compositeFileFilter(redisMetadataStore))
                              .autoCreateDirectory(true)
                              .useWatchService(true)
                              .ignoreHidden(true),
                      e -> e.poller(Pollers
                              .fixedDelay(Duration.ofSeconds(5))
                              .maxMessagesPerPoll(1)
                              .transactional(new TransactionInterceptorBuilder()
                                      .isolation(Isolation.READ_COMMITTED)
                                      .timeout(30).build()
                              )
                              .errorChannel(AppConstants.ERROR_CHANNEL)
                      ))
              .filter(File.class, fileIntegrityValidator::validateAndStore)
              .enrichHeaders(h -> h
                      .header(FileHeaders.ORIGINAL_FILE, "payload.absolutePath")
                      .headerExpression("fileName", "payload.name")
                      .header("processingStartTime", Instant.now())
                      .errorChannel(AppConstants.ERROR_CHANNEL))
              .handle(excelReadingHandler)
              .handle(batchSplittingHandler)
              .split()
              .handle(batchEnrichmentHandler)
              .handle(batchWriterHandler)
              .aggregate(aggregator -> aggregator
                      .correlationExpression("headers.fileName")
                      .releaseStrategy(new SimpleSequenceSizeReleaseStrategy())
                      .groupTimeout(60000)
                      .sendPartialResultOnExpiry(true)
                      .outputProcessor(fileAggregationHandler::aggregateBatches)
//                      .messageStore(redisMessageStore)
//                      .expireGroupsUponCompletion(true)
              ).get();
   }

   @Bean
   public CompositeFileListFilter<File> compositeFileFilter(RedisMetadataStore redisMetadataStore) {
      CompositeFileListFilter<File> compositeFilter = new CompositeFileListFilter<>();
      compositeFilter.addFilter(new SimplePatternFileListFilter("*.xlsx"));
      compositeFilter.addFilter(
              new FileSystemPersistentAcceptOnceFileListFilter(redisMetadataStore, "file:metadata:")
      );

      return compositeFilter;
   }
}
