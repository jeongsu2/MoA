package com.moa.comment;

import com.moa.claim.Claim;
import com.moa.claim.ClaimService;
import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import com.moa.post.PostStatus;
import com.moa.post.Product;
import com.moa.post.ProductKey;
import com.moa.post.ProductRepository;
import com.moa.post.SalePost;
import com.moa.post.SalePostRepository;
import com.moa.user.User;
import com.moa.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final Clock clock;
    private final SalePostRepository salePostRepository;
    private final ProductRepository productRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ClaimService claimService;

    public CommentService(Clock clock, SalePostRepository salePostRepository, ProductRepository productRepository,
            CommentRepository commentRepository, UserRepository userRepository, ClaimService claimService) {
        this.clock = clock;
        this.salePostRepository = salePostRepository;
        this.productRepository = productRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.claimService = claimService;
    }

    // 댓글 저장과 당첨 판정은 한 트랜잭션 (04 문서 AC-7). 외부 호출은 없고 DB 작업만 한다
    @Transactional
    public CommentCreateResponse createComment(Long userId, Long postId, String content) {
        SalePost post = salePostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        // 열기 전 글은 없는 글과 같게 404, 닫힌 글은 새 댓글을 받지 않는다 (03 문서 Q-2 A)
        if (post.getStatus() == PostStatus.DRAFT) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getStatus() == PostStatus.CLOSED) {
            throw new ApiException(ErrorCode.POST_CLOSED);
        }
        User user = userRepository.getReferenceById(userId);

        Optional<ProductKey> matched = findOrderTarget(post, userId, content);
        if (matched.isEmpty()) {
            Comment comment = commentRepository.save(Comment.normal(post, user, content, now()));
            return CommentCreateResponse.of(comment, null, null);
        }

        // 상품 행을 먼저 잠그고 나서 댓글을 넣는다. 댓글 INSERT가 FK 검사로 상품 행에 공유 락을 먼저 잡으면,
        // 여러 요청이 공유 락을 쥔 채 서로의 배타 락을 기다려 데드락이 난다
        Product product = productRepository.findByIdForUpdate(matched.get().productId())
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        // 시각은 락을 잡은 뒤에 읽는다. 락 대기만큼 5분이 줄어들지 않고, 댓글 시각이 당첨 순서와 맞는다
        LocalDateTime now = now();
        boolean quotaAcquired = claimService.acquireQuota(product, postId, userId);
        // 기다릴 락을 모두 잡은 뒤 넣는다. 댓글이 id를 받고 커밋되기까지 기다림이 없어서
        // 작은 id 댓글이 큰 id 댓글보다 한참 늦게 커밋되는 일을 줄인다 (피드 afterId 누락 방지)
        Comment comment = commentRepository.save(Comment.order(post, user, product, content, now));
        // 당첨이 안 돼도 ORDER 댓글로 남아 차순위 후보가 된다 (04 문서 AC-6, AC-11)
        Claim claim = quotaAcquired ? claimService.win(product, comment, user, now) : null;
        return CommentCreateResponse.of(comment, product.getId(), claim);
    }

    // 마이크로초로 잘라 DB datetime(6)에 저장된 값과 응답 값을 같게 한다
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    // 판매자가 자기 글에 쓴 댓글은 주문으로 보지 않는다. 공지 댓글이 실수로 당첨되지 않게 (04 문서 Q-2 A, 정수 확인 전)
    private Optional<ProductKey> findOrderTarget(SalePost post, Long userId, String content) {
        if (post.isWrittenBy(userId)) {
            return Optional.empty();
        }
        return OrderMatcher.match(content, productRepository.findKeysByPostId(post.getId()));
    }
}
