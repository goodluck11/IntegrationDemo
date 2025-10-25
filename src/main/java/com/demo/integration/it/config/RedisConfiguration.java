package com.demo.integration.it.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.redis.metadata.RedisMetadataStore;
import org.springframework.integration.redis.store.RedisMessageStore;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;

/*
 * @created by 24/10/2025  - 00:48
 * @project IntegrationDemo
 * @author Goodluck
 */
@Configuration
public class RedisConfiguration {
   @Bean
   public LockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory) {
      return new RedisLockRegistry(connectionFactory, "file-processor-locks", 60000);
   }

   @Bean
   public LettuceConnectionFactory redisConnectionFactory() {
      return new LettuceConnectionFactory();
   }

   @Bean
   public RedisMetadataStore redisMetadataStore(RedisConnectionFactory redisConnectionFactory) {
      return new RedisMetadataStore(redisConnectionFactory);
   }

   @Bean
   public RedisMessageStore redisMessageStore(RedisConnectionFactory connectionFactory) {
      RedisMessageStore store = new RedisMessageStore(connectionFactory, "file-messages");
      store.setValueSerializer(new GenericJackson2JsonRedisSerializer());
      return store;
   }

   @Bean
   public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
      RedisTemplate<String, String> template = new RedisTemplate<>();
      template.setConnectionFactory(connectionFactory);
      template.setKeySerializer(new StringRedisSerializer());
      template.setValueSerializer(new StringRedisSerializer());
      template.setHashKeySerializer(new StringRedisSerializer());
      template.setHashValueSerializer(new StringRedisSerializer());
      template.afterPropertiesSet();
      return template;
   }

   @Bean
   public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
      return new StringRedisTemplate(connectionFactory);
   }
}
