package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/** A hosted Redis url with the rediss scheme has to turn on TLS and carry the password. */
@SpringBootTest(properties = {"chatads.db.url=", "chatads.cache.enabled=true",
        "spring.data.redis.url=rediss://default:s3cret@eager-owl-1234.upstash.io:6379",
        "spring.data.redis.timeout=1500ms", "spring.data.redis.connect-timeout=2s"})
class RedisUrlConfigTest {

    @Autowired LettuceConnectionFactory factory;

    @Test
    void redissUrlEnablesTlsAndPassword() {
        RedisStandaloneConfiguration standalone = factory.getStandaloneConfiguration();
        assertTrue(factory.isUseSsl());
        assertEquals("eager-owl-1234.upstash.io", standalone.getHostName());
        assertEquals(6379, standalone.getPort());
        assertEquals("default", standalone.getUsername());
        assertEquals("s3cret", new String(standalone.getPassword().get()));
    }

    @Test
    void timeoutsComeFromConfiguration() {
        assertEquals(Duration.ofMillis(1500), factory.getClientConfiguration().getCommandTimeout());
    }
}
