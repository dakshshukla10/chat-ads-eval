package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** With the cache off, Redis is not set up at all, so an unfilled url variable cannot break startup. */
@SpringBootTest(properties = {"chatads.db.url=", "spring.data.redis.url="})
class RedisOffByDefaultTest {

    @Autowired ApplicationContext context;

    @Test
    void noRedisBeansWithoutTheCache() {
        assertEquals(0, context.getBeanNamesForType(RedisConnectionFactory.class).length);
    }
}
