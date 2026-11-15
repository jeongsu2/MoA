package com.moa.feed;

import com.moa.claim.ActiveClaimView;
import com.moa.claim.ClaimRepository;
import com.moa.claim.ClaimStatus;
import com.moa.comment.CommentRepository;
import com.moa.comment.CommentView;
import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import com.moa.post.Product;
import com.moa.post.ProductRepository;
import com.moa.post.SalePost;
import com.moa.post.SalePostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 읽기만 한다. 만료, 승계 같은 쓰기는 일으키지 않는다 (04 문서 3장). 폴링이 몰려도 락을 만들지 않는다
@Service
public class FeedService {

    public static final int COMMENT_PAGE_SIZE = 100;
    // afterId보다 작은 id라도 이 시간 안에 만든 댓글은 다시 준다. 댓글 INSERT에서 커밋까지 걸리는 시간보다 넉넉하게
    public static final Duration RECENT_OVERLAP = Duration.ofSeconds(5);

    private final Clock clock;
    private final SalePostRepository salePostRepository;
    private final ProductRepository productRepository;
    private final ClaimRepository claimRepository;
    private final CommentRepository commentRepository;

    public FeedService(Clock clock, SalePostRepository salePostRepository, ProductRepository productRepository,
            ClaimRepository claimRepository, CommentRepository commentRepository) {
        this.clock = clock;
        this.salePostRepository = salePostRepository;
        this.productRepository = productRepository;
        this.claimRepository = claimRepository;
        this.commentRepository = commentRepository;
    }

    // 비로그인이면 viewerId가 null. 한 트랜잭션 안의 조회는 같은 스냅샷을 봐서 상품 상태와 댓글이 서로 어긋나지 않는다
    @Transactional(readOnly = true)
    public FeedResponse getFeed(Long postId, Long viewerId, long afterId) {
        SalePost post = salePostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        if (post.isDraft() && !post.isWrittenBy(viewerId)) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        boolean viewerIsSeller = post.isWrittenBy(viewerId);

        List<FeedProductResponse> products = findProducts(postId, viewerIsSeller);

        // 하나 더 읽어서 다음 쪽이 있는지 안다
        List<CommentView> comments = commentRepository.findFeedComments(postId, afterId,
                now.minus(RECENT_OVERLAP), PageRequest.of(0, COMMENT_PAGE_SIZE + 1));
        boolean hasMore = comments.size() > COMMENT_PAGE_SIZE;
        if (hasMore) {
            comments = comments.subList(0, COMMENT_PAGE_SIZE);
        }

        List<MyClaimResponse> myClaims = viewerId == null
                ? List.of()
                : claimRepository.findMyClaims(postId, viewerId, ClaimStatus.IN_PROGRESS).stream()
                        .map(MyClaimResponse::from)
                        .toList();
        return new FeedResponse(now, post.getStatus(), products, comments, hasMore, myClaims);
    }

    // 당첨 표시는 product.current_claim_id로 그린다. 예전 댓글의 당첨이 바뀌어도 afterId 폴링에 반영된다 (AC-17)
    private List<FeedProductResponse> findProducts(Long postId, boolean viewerIsSeller) {
        List<Product> products = productRepository.findByPostIdOrderByIdAsc(postId);
        List<Long> claimIds = products.stream()
                .map(Product::getCurrentClaimId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ActiveClaimView> claims = claimIds.isEmpty()
                ? Map.of()
                : claimRepository.findActiveViews(claimIds).stream()
                        .collect(Collectors.toMap(ActiveClaimView::claimId, Function.identity()));
        return products.stream()
                .map(product -> {
                    ActiveClaimView claim = product.getCurrentClaimId() == null
                            ? null
                            : claims.get(product.getCurrentClaimId());
                    return new FeedProductResponse(product.getId(), product.getItemNo(), product.getStatus(),
                            claim == null ? null : FeedClaimResponse.of(claim, viewerIsSeller));
                })
                .toList();
    }
}
