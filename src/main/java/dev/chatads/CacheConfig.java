package dev.chatads;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis auto-configuration only runs when the cache is on. A service that does not use Redis then
 * pays nothing for it at startup and cannot fail on a half-filled Redis setting.
 */
@Configuration
@ConditionalOnProperty(name = "chatads.cache.enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
class CacheConfig {
}
