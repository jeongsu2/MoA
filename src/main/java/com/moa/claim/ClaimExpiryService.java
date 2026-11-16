package com.moa.claim;

import com.moa.post.ProductRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// 5분 만료 (06 문서 AC-2~AC-8). 타이머를 메모리에 두지 않고 DB의 deadline_at만 본다.
// 서버가 꺼져 있던 동안 지난 마감도 다음 실행에서 그대로 처리된다 (AC-6)
@Service
public class ClaimExpiryService {

    private final Clock clock;
    private final ClaimRepository claimRepository;
    private final ProductRepository productRepository;
    private final ClaimService claimService;

    public ClaimExpiryService(Clock clock, ClaimRepository claimRepository, ProductRepository productRepository,
            ClaimService claimService) {
        this.clock = clock;
        this.claimRepository = claimRepository;
        this.productRepository = productRepository;
        this.claimService = claimService;
    }

    // 고르는 데만 쓴다. 실제 만료 여부는 expire의 조건부 UPDATE가 락 뒤의 시각으로 다시 판단한다
    @Transactional(readOnly = true)
    public List<Long> findDueClaimIds(int limit) {
        return claimRepository.findDueClaimIds(now(), PageRequest.of(0, limit));
    }

    // claim 하나 = 트랜잭션 하나 (AC-7). 만료하고 승계했으면 true, 이미 끝났거나 아직 마감 전이면 false.
    // 락 전에 claim을 읽으므로 READ COMMITTED로 연다. 바깥 트랜잭션 안에서 부르면 이 설정이 안 걸리니
    // 트랜잭션 없이 부른다 (claim-implementation-notes 7번)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean expire(Long claimId) {
        Optional<ClaimTarget> found = claimRepository.findTarget(claimId);
        if (found.isEmpty()) {
            return false;
        }
        ClaimTarget target = found.get();
        // 신고, 입금확인, 입금 안 됨과 같은 상품 행 락에 한 줄로 선다 (notes 4번)
        productRepository.findByIdForUpdate(target.productId())
                .orElseThrow(() -> new IllegalStateException("당첨의 상품이 없음: " + target.productId()));
        // 시각은 락을 잡은 뒤에 읽는다
        LocalDateTime now = now();
        // 겹친 실행이나 먼저 커밋된 신고가 있으면 0행이라 아무것도 안 한다 (AC-4, AC-5)
        if (claimRepository.expire(claimId, now) != 1) {
            return false;
        }
        claimService.releaseEndedClaim(target.productId(), target.postId(), claimId, target.buyerId(), now);
        return true;
    }

    // 마이크로초로 잘라 DB datetime(6)에 저장된 값과 같게 한다
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
