package dev.chatads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// The data source is built by hand in RetrievalConfig, only when a database url is set. Redis is
// switched on by CacheConfig, only when the cache is enabled.
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class})
public class ChatAdsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatAdsApplication.class, args);
    }
}
