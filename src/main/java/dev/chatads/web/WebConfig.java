package dev.chatads.web;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    /** Off unless {@code chatads.rate-limit.per-minute} is above zero. */
    @Bean
    @ConditionalOnExpression("${chatads.rate-limit.per-minute:0} > 0")
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(@Value("${chatads.rate-limit.per-minute}") int perMinute) {
        FilterRegistrationBean<RateLimitFilter> bean =
                new FilterRegistrationBean<>(new RateLimitFilter(perMinute, Clock.systemUTC()));
        bean.addUrlPatterns("/v1/*");
        return bean;
    }
}
