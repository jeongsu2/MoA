package com.moa.claim;

import com.moa.comment.Comment;
import com.moa.post.Product;
import com.moa.post.ProductRepository;
import com.moa.post.ProductStatus;
import com.moa.user.User;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final PurchaseQuotaRepository purchaseQuotaRepository;
    private final ProductRepository productRepository;

    public ClaimService(ClaimRepository claimRepository, PurchaseQuotaRepository purchaseQuotaRepository,
            ProductRepository productRepository) {
        this.claimRepository = claimRepository;
        this.purchaseQuotaRepository = purchaseQuotaRepository;
        this.productRepository = productRepository;
    }

    // 아래 두 메서드는 부르는 쪽이 이미 product 행을 FOR UPDATE로 잡고 있어야 한다. 그래서 새 트랜잭션을 열지 않고 꼭 이어받는다.
    // 순서: product(이미 잠금) -> purchase_quota(acquireQuota) -> 댓글 INSERT(부르는 쪽) -> claim INSERT, product UPDATE(win).
    // 기다릴 수 있는 락을 댓글 INSERT 전에 다 잡아서, 댓글이 id를 받은 뒤 커밋까지 기다림이 없다 (피드 afterId 누락 방지).
    // quota를 못 얻으면 상품을 건드리지 않아서, 당첨 못 할 사람이 상품을 막지 않는다 (04 테스트 계획 Q-02)
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean acquireQuota(Product lockedProduct, Long postId, Long buyerId) {
        if (lockedProduct.getStatus() != ProductStatus.ON_SALE) {
            return false;
        }
        purchaseQuotaRepository.createIfAbsent(postId, buyerId);
        return purchaseQuotaRepository.increase(postId, buyerId, PurchaseQuota.MAX_ACTIVE_COUNT) == 1;
    }

    // acquireQuota가 true일 때만 부른다
    @Transactional(propagation = Propagation.MANDATORY)
    public Claim win(Product lockedProduct, Comment comment, User buyer, LocalDateTime now) {
        Claim claim = claimRepository.save(Claim.won(lockedProduct, comment, buyer, now));
        if (productRepository.reserve(lockedProduct.getId(), claim.getId()) != 1) {
            // 락을 잡았는데도 실패하면 상품 상태가 어긋난 것. 예외로 claim과 quota까지 모두 롤백한다
            throw new IllegalStateException("상품 " + lockedProduct.getId() + "의 진행 중 당첨을 연결하지 못함");
        }
        return claim;
    }
}
