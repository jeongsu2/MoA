package com.moa;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 앱의 Clock 빈 대신 이 고정 시계가 주입된다
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(IntegrationTestSupport.FIXED_NOW, ZoneId.systemDefault());
    }
}
