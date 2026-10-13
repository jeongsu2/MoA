package com.moa.global;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 당첨 시각, 마감 시각, 만료 판정은 모두 이 시계 하나로 읽는다. DB NOW()는 쓰지 않는다 (claim-implementation-notes 1번).
// 테스트는 이 빈 대신 고정 시계를 넣어 5분 경계를 정확히 맞춘다
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
