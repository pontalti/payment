package com.payment.adapter.persistence.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.NamedCacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis cache configuration for the {@code process} bounded context.
 *
 * <p>Cache values are serialized as plain JSON by {@link JacksonJsonRedisSerializer},
 * which is bound to a concrete type. Because the target type is known at read time,
 * no {@code @class} type markers are written — unlike the generic serializer, which
 * needs them to reconstruct the object and pollutes the payload with
 * {@code "@class"} entries and {@code ["java.math.BigDecimal", ...]} wrappers.
 *
 * <p>The trade-off is that each cache is tied to a single value type. A cache holding
 * a different type needs its own {@link RedisCacheConfiguration}, built via
 * {@link #forType(RedisCacheConfiguration, ObjectMapper, Class)}, plus its own
 * {@link CacheResolver} bean.
 *
 * <p>{@code cacheDefaults} is deliberately left without a value serializer: no single
 * type can serve as a sensible default once more than one cache exists. A cache name
 * that is not explicitly registered therefore fails fast on first use with
 * {@code IllegalStateException: Cannot serialize value of type ... without a serializer},
 * rather than silently writing entries with the wrong serializer.
 *
 * <p>Cache names come from configuration, so annotations reference a
 * {@link CacheResolver} bean by name instead of using {@code cacheNames} — annotation
 * attributes require compile-time constants and cannot hold resolved property values.
 *
 * <p>Configuration properties:
 * <pre>
 * cache:
 * 	 ttl: 10m
 * 	 payments:
 * 	   name: payments
 * </pre>
 *
 * @see PaymentIdMixin
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String PAYMENTS_CACHE_PROPERTY = "${cache.payments.name}";
    public static final String CACHE_TTL_PROPERTY = "${cache.ttl}";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
            @Value(PAYMENTS_CACHE_PROPERTY) String paymentsCache,
            @Value(CACHE_TTL_PROPERTY) Duration ttl) {

        ObjectMapper mapper = JsonMapper.builder()
                .addMixIn(PaymentId.class, PaymentIdMixin.class)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withCacheConfiguration(paymentsCache, forType(base, mapper, Payment.class))
                .build();
    }

    private static RedisCacheConfiguration forType(RedisCacheConfiguration base,
            ObjectMapper mapper, Class<?> type) {
        return base.serializeValuesWith(SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(mapper, type)));
    }
    
    @Bean("paymentCacheResolver")
    public CacheResolver paymentCacheResolver(CacheManager cm, @Value(PAYMENTS_CACHE_PROPERTY) String name) {
        return new NamedCacheResolver(cm, name);
    }
}