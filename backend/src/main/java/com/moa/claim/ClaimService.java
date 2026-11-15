package com.moa.claim;

import com.moa.comment.Comment;
import com.moa.comment.CommentRepository;
import com.moa.comment.OrderCandidate;
import com.moa.post.PostStatus;
import com.moa.post.Product;
import com.moa.post.ProductRepository;
import com.moa.post.ProductStatus;
import com.moa.post.SalePostRepository;
import com.moa.user.User;
import com.moa.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimService {

    private static final Pageable FIRST_ONLY = PageRequest.of(0, 1);
    // 후보 조회에서 "1을 빼고 볼 사람"이 없을 때 넘기는 값. id는 1부터라 아무와도 같지 않다
    private static final long NO_USER = 0L;

    private final ClaimRepository claimRepository;
    private final PurchaseQuotaRepository purchaseQuotaRepository;
    private final ProductRepository productRepository;
    private final CommentRepository commentRepository;
    private final SalePostRepository salePostRepository;
    private final UserRepository userRepository;

    public ClaimService(ClaimRepository claimRepository, PurchaseQuotaRepository purchaseQuotaRepository,
            ProductRepository productRepository, CommentRepository commentRepository,
            SalePostRepository salePostRepository, UserRepository userRepository) {
        this.claimRepository = claimRepository;
        this.purchaseQuotaRepository = purchaseQuotaRepository;
        this.productRepository = productRepository;
        this.commentRepository = commentRepository;
        this.salePostRepository = salePostRepository;
        this.userRepository = userRepository;
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

    // claim이 확정 없이 끝난 뒤(입금 안 됨, 만료) 부르는 승계 하나 (06 문서 AC-9). 부르는 쪽이 product 행을 잡고 claim을
    // 조건부 UPDATE로 끝낸 상태여야 한다. 끝난 구매자의 quota를 돌려주고 차순위에게 새 5분(now부터)을 준다.
    // 후보가 없거나 글이 닫혔으면 상품을 판매 중으로 되돌린다 (AC-14, AC-17). 승계된 claim을 돌려준다
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Claim> releaseEndedClaim(Long lockedProductId, Long postId, Long endedClaimId, Long endedBuyerId,
            LocalDateTime now) {
        // 아직 quota를 돌려받지 않은 끝난 구매자. 돌려주면 null
        Long returningBuyerId = endedBuyerId;
        // 닫힌 글은 진행 중 당첨만 끝까지 가고 승계는 멈춘다 (03 문서 Q-2 A)
        boolean open = salePostRepository.findStatusById(postId).filter(PostStatus.OPEN::equals).isPresent();
        long afterId = 0;
        while (open) {
            long excluded = returningBuyerId == null ? NO_USER : returningBuyerId;
            List<OrderCandidate> found = commentRepository.findOrderCandidates(lockedProductId, postId, afterId,
                    excluded, PurchaseQuota.MAX_ACTIVE_COUNT, FIRST_ONLY);
            if (found.isEmpty()) {
                break;
            }
            OrderCandidate candidate = found.get(0);
            // quota 두 행(끝난 구매자, 후보)은 user_id 작은 순서로 잡는다. 엇갈린 두 승계가 서로를 기다리지 않게 (notes 5번).
            // 같은 사람이면 먼저 돌려받고 다시 얻는다 (Q-2 A)
            if (returningBuyerId != null && returningBuyerId <= candidate.userId()) {
                decrease(postId, returningBuyerId, endedClaimId);
                returningBuyerId = null;
            }
            purchaseQuotaRepository.createIfAbsent(postId, candidate.userId());
            if (purchaseQuotaRepository.increase(postId, candidate.userId(), PurchaseQuota.MAX_ACTIVE_COUNT) == 1) {
                if (returningBuyerId != null) {
                    decrease(postId, returningBuyerId, endedClaimId);
                }
                return Optional.of(handOver(lockedProductId, endedClaimId, candidate, now));
            }
            // 락 없이 고른 사이에 다른 상품 당첨으로 5개가 찼다. 다음 후보를 본다 (D-13).
            // 이 드문 경우에만 뒤 후보의 quota를 작은 user_id 순서가 아니게 잡을 수 있다
            afterId = candidate.commentId();
        }
        if (returningBuyerId != null) {
            decrease(postId, returningBuyerId, endedClaimId);
        }
        if (productRepository.release(lockedProductId, endedClaimId) != 1) {
            throw new IllegalStateException("상품 " + lockedProductId + "을 판매 중으로 되돌리지 못함");
        }
        return Optional.empty();
    }

    // 새 claim(WON, deadline_at = now + 5분)을 만들고 상품의 진행 중 당첨을 바꾼다. 상품은 RESERVED 그대로 (AC-13)
    private Claim handOver(Long lockedProductId, Long endedClaimId, OrderCandidate candidate, LocalDateTime now) {
        Claim next = claimRepository.save(Claim.won(productRepository.getReferenceById(lockedProductId),
                commentRepository.getReferenceById(candidate.commentId()),
                userRepository.getReferenceById(candidate.userId()), now));
        if (productRepository.handOver(lockedProductId, endedClaimId, next.getId()) != 1) {
            throw new IllegalStateException("상품 " + lockedProductId + "의 진행 중 당첨을 넘기지 못함");
        }
        return next;
    }

    private void decrease(Long postId, Long buyerId, Long endedClaimId) {
        if (purchaseQuotaRepository.decrease(postId, buyerId) != 1) {
            throw new IllegalStateException("당첨 " + endedClaimId + "의 구매 수를 돌려주지 못함");
        }
    }
}
