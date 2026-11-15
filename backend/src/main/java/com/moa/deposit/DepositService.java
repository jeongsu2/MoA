package com.moa.deposit;

import com.moa.claim.Claim;
import com.moa.claim.ClaimRepository;
import com.moa.claim.ClaimService;
import com.moa.claim.ClaimStatus;
import com.moa.claim.ClaimTarget;
import com.moa.comment.Comment;
import com.moa.comment.CommentRepository;
import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import com.moa.post.Product;
import com.moa.post.ProductRepository;
import com.moa.post.SalePost;
import com.moa.post.SalePostRepository;
import com.moa.user.Address;
import com.moa.user.AddressRepository;
import com.moa.user.User;
import com.moa.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// 입금완료 신고, 판매자 입금확인, 입금 안 됨 (05 문서).
// 모든 동작은 product 행 FOR UPDATE를 먼저 잡고 claim을 조건부 UPDATE한다 (claim-implementation-notes 4번).
// 격리 수준은 READ COMMITTED. 잠글 상품 id를 알려고 락 전에 claim을 한 번 읽는데, REPEATABLE READ면 그 순간
// 스냅샷이 잡혀 락 뒤의 일반 SELECT가 방금 커밋된 주문, 배송지 변경을 못 본다 (05 테스트 계획 4장).
// 락 뒤에 읽는 값이 늘 최신 커밋이어야 진 요청이 200과 같은 주문을 돌려준다 (notes 7번)
@Service
public class DepositService {

    public static final String THANKS_MESSAGE = "구매감사합니다. 예쁘게 포장해서 전달드릴게요 💓";
    private static final List<ClaimStatus> SELLER_ORDER_STATUSES = List.of(ClaimStatus.REPORTED, ClaimStatus.CONFIRMED);

    private final Clock clock;
    private final ClaimRepository claimRepository;
    private final ProductRepository productRepository;
    private final SalePostRepository salePostRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final RemoteAreaZipCodes remoteAreaZipCodes;
    private final ClaimService claimService;

    public DepositService(Clock clock, ClaimRepository claimRepository, ProductRepository productRepository,
            SalePostRepository salePostRepository, CommentRepository commentRepository,
            UserRepository userRepository, AddressRepository addressRepository,
            PurchaseOrderRepository purchaseOrderRepository, RemoteAreaZipCodes remoteAreaZipCodes,
            ClaimService claimService) {
        this.clock = clock;
        this.claimService = claimService;
        this.claimRepository = claimRepository;
        this.productRepository = productRepository;
        this.salePostRepository = salePostRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.remoteAreaZipCodes = remoteAreaZipCodes;
    }

    // 한 트랜잭션으로 신고, 주문 스냅샷, DEPOSIT_REPORT 댓글 (05 문서 AC-4). 외부 호출은 없다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportResponse report(Long buyerId, Long claimId) {
        // 남의 당첨도 없는 당첨과 같은 404 (AC-7)
        ClaimTarget target = claimRepository.findTarget(claimId)
                .filter(found -> found.buyerId().equals(buyerId))
                .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND));
        Product product = lockProduct(target);
        int price = product.getPrice();
        // 시각은 락을 잡은 뒤에 읽는다. 락을 기다린 시간만큼 앞선 시각으로 판정하지 않는다
        LocalDateTime now = now();

        if (claimRepository.report(claimId, buyerId, now) == 1) {
            PurchaseOrder order = createOrder(target, price, now);
            commentRepository.save(Comment.depositReport(salePostRepository.getReferenceById(target.postId()),
                    userRepository.getReferenceById(buyerId), commentRepository.getReferenceById(target.commentId()),
                    now));
            return new ReportResponse(claimId, ClaimStatus.REPORTED, now, OrderSummary.from(order));
        }

        // 진 요청. 앞 요청이 커밋한 뒤 product 락을 얻었으므로 지금 읽으면 그 결과가 보인다
        Claim claim = findClaim(claimId);
        ClaimStatus status = claim.getStatus();
        if (status == ClaimStatus.REPORTED || status == ClaimStatus.CONFIRMED) {
            PurchaseOrder order = purchaseOrderRepository.findByClaimId(claimId)
                    .orElseThrow(() -> new IllegalStateException("신고된 당첨 " + claimId + "에 주문이 없음"));
            return new ReportResponse(claimId, status, claim.getReportedAt(), OrderSummary.from(order));
        }
        // 내 WON인데 UPDATE가 0행이면 deadline_at이 지난 것. 스케줄러가 아직 안 돌았어도 같다 (AC-5)
        if (status == ClaimStatus.WON || status == ClaimStatus.EXPIRED) {
            throw new ApiException(ErrorCode.DEPOSIT_DEADLINE_PASSED);
        }
        throw new ApiException(ErrorCode.CLAIM_STATUS_CONFLICT);
    }

    // 판매자 [입금확인] (05 문서 AC-12, AC-13). 감사 댓글은 UPDATE가 1행일 때만 써서 중복 클릭에도 1개 (D-08)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConfirmResponse confirm(Long sellerId, Long claimId) {
        ClaimTarget target = findTargetForSeller(sellerId, claimId);
        lockProduct(target);
        LocalDateTime now = now();

        if (claimRepository.confirm(claimId, now) == 1) {
            if (productRepository.markSold(target.productId(), claimId) != 1) {
                throw new IllegalStateException("상품 " + target.productId() + "을 판매 완료로 바꾸지 못함");
            }
            commentRepository.save(Comment.system(salePostRepository.getReferenceById(target.postId()),
                    userRepository.getReferenceById(sellerId), commentRepository.getReferenceById(target.commentId()),
                    THANKS_MESSAGE, now));
            return new ConfirmResponse(claimId, ClaimStatus.CONFIRMED, now);
        }

        Claim claim = findClaim(claimId);
        if (claim.getStatus() == ClaimStatus.CONFIRMED) {
            return new ConfirmResponse(claimId, ClaimStatus.CONFIRMED, claim.getConfirmedAt());
        }
        // 신고 전(WON), 이미 입금 안 됨, 만료는 확인할 수 없다 (AC-15, AC-18)
        throw new ApiException(ErrorCode.CLAIM_STATUS_CONFLICT);
    }

    // 판매자 [입금 안 됨] (05 문서 AC-17, AC-19). 주문 행은 기록으로 남긴다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RejectResponse reject(Long sellerId, Long claimId) {
        ClaimTarget target = findTargetForSeller(sellerId, claimId);
        lockProduct(target);
        LocalDateTime now = now();

        if (claimRepository.reject(claimId, now) == 1) {
            // 만료와 같은 승계 로직 (06 문서 AC-9)
            RejectResponse.NextClaim nextClaim = claimService.releaseEndedClaim(target.productId(), target.postId(),
                    claimId, target.buyerId(), now).map(RejectResponse.NextClaim::from).orElse(null);
            return new RejectResponse(claimId, ClaimStatus.REJECTED, nextClaim);
        }

        // 다시 누른 요청은 승계를 또 하지 않는다 (05 문서 AC-19). 그 사이 승계된 claim은 피드에서 본다
        if (findClaim(claimId).getStatus() == ClaimStatus.REJECTED) {
            return new RejectResponse(claimId, ClaimStatus.REJECTED, null);
        }
        throw new ApiException(ErrorCode.CLAIM_STATUS_CONFLICT);
    }

    // 그 글의 판매자만 (05 문서 AC-21). 없는 글은 404, 다른 사람의 글은 403
    @Transactional(readOnly = true)
    public List<SellerOrderResponse> getOrders(Long sellerId, Long postId) {
        SalePost post = salePostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        if (!post.isWrittenBy(sellerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return purchaseOrderRepository.findSellerOrders(postId, SELLER_ORDER_STATUSES);
    }

    // 없는 당첨은 404, 그 글의 판매자가 아니면 403. 역할이 아니라 글 작성자와 요청자를 비교한다 (AC-16)
    private ClaimTarget findTargetForSeller(Long sellerId, Long claimId) {
        ClaimTarget target = claimRepository.findTarget(claimId)
                .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND));
        if (!target.sellerId().equals(sellerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return target;
    }

    // 락 순서: product(이미 잠금) -> users. 기본 배송지는 users 행 FOR UPDATE 아래에서 읽어서
    // 배송지 변경(AddressService, users 락만 잡음)과 한 줄로 선다. 주소가 반쯤 바뀐 상태를 읽지 않는다 (AC-9)
    private PurchaseOrder createOrder(ClaimTarget target, int price, LocalDateTime now) {
        User buyer = userRepository.findByIdForUpdate(target.buyerId())
                .orElseThrow(() -> new IllegalStateException("당첨자가 없음: " + target.buyerId()));
        Address address = addressRepository.findByUserIdAndIsDefaultTrue(target.buyerId())
                .orElseThrow(() -> new IllegalStateException("기본 배송지가 없음: " + target.buyerId()));
        SalePost post = salePostRepository.findById(target.postId())
                .orElseThrow(() -> new IllegalStateException("글이 없음: " + target.postId()));
        // 배송비는 코드 상수가 아니라 글에 저장된 값. 당첨마다 따로 받는다 (D-25, D-26)
        int shippingFee = remoteAreaZipCodes.isRemote(address.getZipCode())
                ? post.getRemoteShippingFee()
                : post.getShippingFee();
        return purchaseOrderRepository.save(PurchaseOrder.snapshot(claimRepository.getReferenceById(target.claimId()),
                buyer, address, price, shippingFee, now));
    }

    private Product lockProduct(ClaimTarget target) {
        return productRepository.findByIdForUpdate(target.productId())
                .orElseThrow(() -> new IllegalStateException("당첨의 상품이 없음: " + target.productId()));
    }

    private Claim findClaim(Long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalStateException("당첨이 없음: " + claimId));
    }

    // 마이크로초로 잘라 DB datetime(6)에 저장된 값과 응답 값을 같게 한다
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
