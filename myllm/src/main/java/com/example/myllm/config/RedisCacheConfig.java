package com.example.myllm.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置
 * ---------------
 * 按 domain 设置不同 TTL:
 *   history_sessions - 30s
 *   session_msgs     - 30min
 *   model_list       - 5min
 *   memory_list      - 5min
 *   rag_list         - 5min
 *   rag_search       - 5min
 */
@Configuration
public class RedisCacheConfig implements CachingConfigurer {

    /** Redis 不可用时自动降级：记录日志后直接查 MySQL，不抛异常。 */
    @Override
    @NonNull
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(@NonNull RuntimeException e, @NonNull Cache cache, @NonNull Object key) {
                System.err.println("[Cache] GET " + cache.getName() + ":" + key + " → Redis不可用, 降级MySQL");
            }
            @Override public void handleCachePutError(@NonNull RuntimeException e, @NonNull Cache cache, @NonNull Object key, Object value) {
                System.err.println("[Cache] PUT " + cache.getName() + ":" + key + " → Redis不可用");
            }
            @Override public void handleCacheEvictError(@NonNull RuntimeException e, @NonNull Cache cache, @NonNull Object key) {
                System.err.println("[Cache] EVICT " + cache.getName() + ":" + key + " → Redis不可用");
            }
            @Override public void handleCacheClearError(@NonNull RuntimeException e, @NonNull Cache cache) {
                System.err.println("[Cache] CLEAR " + cache.getName() + " → Redis不可用");
            }
        };
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> custom = new HashMap<>();
        custom.put("history_sessions", defaults.entryTtl(Duration.ofSeconds(30)));
        custom.put("session_msgs", defaults.entryTtl(Duration.ofMinutes(30)));
        custom.put("model_list", defaults.entryTtl(Duration.ofMinutes(5)));
        custom.put("memory_list", defaults.entryTtl(Duration.ofMinutes(5)));
        custom.put("rag_list", defaults.entryTtl(Duration.ofMinutes(5)));
        custom.put("rag_search", defaults.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(custom)
                .build();
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());  // 支持 LocalDateTime 序列化
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
