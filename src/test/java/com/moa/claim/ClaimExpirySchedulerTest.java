package com.moa.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

// 스케줄러 반복 로직 단위 테스트 (06 테스트 계획 E-07)
class ClaimExpirySchedulerTest {

    @Test
    @DisplayName("E-07 3개 중 두 번째가 데드락으로 실패해도 첫째와 셋째는 처리. 실패한 것은 다음 실행에서 다시 시도")
    void oneFailureDoesNotStopOthers() {
        ClaimExpiryService service = mock(ClaimExpiryService.class);
        when(service.findDueClaimIds(anyInt())).thenReturn(List.of(1L, 2L, 3L));
        when(service.expire(1L)).thenReturn(true);
        when(service.expire(2L)).thenThrow(new CannotAcquireLockException("Deadlock found"));
        when(service.expire(3L)).thenReturn(true);
        ClaimExpiryScheduler scheduler = new ClaimExpiryScheduler(service);

        assertThat(scheduler.expireDueClaims()).isEqualTo(2);
        verify(service).expire(1L);
        verify(service).expire(3L);
        verify(service).findDueClaimIds(ClaimExpiryScheduler.BATCH_SIZE);

        // 다음 실행에서 2번이 다시 대상이 되면 이번에는 처리된다
        when(service.findDueClaimIds(anyInt())).thenReturn(List.of(2L));
        doReturn(true).when(service).expire(2L);
        assertThat(scheduler.expireDueClaims()).isEqualTo(1);
    }
}
