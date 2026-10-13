package com.moa.claim;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

// 만료와 승계는 이 스케줄러만 한다. 피드 조회는 쓰기를 하지 않는다 (06 문서 Q-3 A, 정수 확인 전).
// 서버 1대 전제라 중복 실행 방지(ShedLock)는 없다. 겹쳐 돌아도 조건부 UPDATE로 한 번만 바뀐다 (AC-4)
@Component
public class ClaimExpiryScheduler {

    // 한 번에 읽는 수. 1초마다 도니 초당 이만큼까지 처리한다
    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(ClaimExpiryScheduler.class);

    private final ClaimExpiryService claimExpiryService;

    public ClaimExpiryScheduler(ClaimExpiryService claimExpiryService) {
        this.claimExpiryService = claimExpiryService;
    }

    // 앞 실행이 끝나고 1초 뒤. moa.scheduler.enabled=false면 자동 실행이 꺼지고 테스트가 직접 부른다
    @Scheduled(fixedDelay = 1000)
    public void run() {
        expireDueClaims();
    }

    // 이 메서드는 트랜잭션 없이 돈다. claim마다 expire가 따로 트랜잭션을 연다.
    // 하나가 실패해도(데드락, 락 대기 초과) 나머지는 처리하고, 실패한 claim은 WON 그대로라 다음 실행에서 다시 시도한다 (AC-7)
    public int expireDueClaims() {
        List<Long> claimIds = claimExpiryService.findDueClaimIds(BATCH_SIZE);
        int expired = 0;
        for (Long claimId : claimIds) {
            try {
                if (claimExpiryService.expire(claimId)) {
                    expired++;
                }
            } catch (DataAccessException | TransactionException | IllegalStateException e) {
                log.warn("당첨 {} 만료 실패, 다음 실행에서 다시 시도", claimId, e);
            }
        }
        return expired;
    }
}
