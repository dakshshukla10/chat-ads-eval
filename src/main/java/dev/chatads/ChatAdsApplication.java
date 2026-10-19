package dev.chatads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// The data source is built by hand in PostgresConfig, only when a database url is set.
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class ChatAdsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatAdsApplication.class, args);
    }
}
