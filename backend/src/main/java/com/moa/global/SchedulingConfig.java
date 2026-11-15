package com.moa.global;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 자동 실행 스위치. 테스트 프로필은 끄고, 테스트가 실행 시점을 직접 정한다 (06 테스트 계획 0장).
// 꺼도 스케줄러 빈은 남아 있어서 테스트가 주입받아 부를 수 있다
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "moa.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
