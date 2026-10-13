package com.moa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

// 테스트용 시계. 멈춰 있다가 테스트가 정한 시각으로만 움직인다. 5분 경계(#30, #31)를 정확히 맞출 때 쓴다
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;
    // 동시성 테스트에서 스레드마다 다른 시각을 줄 때 쓴다 (06 테스트 계획 E-05). 없으면 공용 시각
    private final ThreadLocal<Instant> threadInstant = new ThreadLocal<>();

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.zone = zone;
        this.instant = new AtomicReference<>(start.atZone(zone).toInstant());
    }

    public void setTo(LocalDateTime time) {
        instant.set(time.atZone(zone).toInstant());
    }

    // 이 스레드에서만 time을 돌려준다. 작업이 끝나면 clearCurrentThread로 지운다
    public void setForCurrentThread(LocalDateTime time) {
        threadInstant.set(time.atZone(zone).toInstant());
    }

    public void clearCurrentThread() {
        threadInstant.remove();
    }

    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(LocalDateTime.ofInstant(instant.get(), zone), zone);
    }

    @Override
    public Instant instant() {
        Instant own = threadInstant.get();
        return own == null ? instant.get() : own;
    }
}
