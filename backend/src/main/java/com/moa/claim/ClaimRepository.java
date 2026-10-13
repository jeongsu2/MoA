package com.moa.claim;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @Query("select new com.moa.claim.ActiveClaimView(c.id, c.product.id, c.comment.id, u.nickname, u.realName,"
            + " c.status, c.deadlineAt)"
            + " from Claim c join c.buyer u where c.id in :claimIds")
    List<ActiveClaimView> findActiveViews(@Param("claimIds") Collection<Long> claimIds);

    @Query("select new com.moa.claim.MyClaimView(c.id, p.id, c.status, c.deadlineAt, sp.bankName,"
            + " sp.accountNumber, sp.accountHolder, p.price, post.shippingFee, post.remoteShippingFee)"
            + " from Claim c join c.product p join p.post post"
            + " join SellerProfile sp on sp.userId = post.seller.id"
            + " where post.id = :postId and c.buyer.id = :buyerId and c.status in :statuses order by c.id asc")
    List<MyClaimView> findMyClaims(@Param("postId") Long postId, @Param("buyerId") Long buyerId,
            @Param("statuses") Collection<ClaimStatus> statuses);

    @Query("select new com.moa.claim.ClaimTarget(c.id, p.id, post.id, post.seller.id, c.buyer.id, c.comment.id)"
            + " from Claim c join c.product p join p.post post where c.id = :claimId")
    Optional<ClaimTarget> findTarget(@Param("claimId") Long claimId);

    // 아래 전이는 모두 부르는 쪽이 product 행을 FOR UPDATE로 잡은 뒤 부른다 (claim-implementation-notes 4번).
    // 바뀐 행이 1이면 이긴 요청, 0이면 다른 요청이 먼저 바꿨거나 조건이 안 맞은 것.
    // clearAutomatically는 뒤에서 claim을 다시 읽을 때 1차 캐시의 옛 상태를 보지 않게 한다

    // 입금완료 신고. now < deadline_at일 때만 성공한다. 정확히 5분 0초는 만료 (notes 2번)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Claim c set c.status = com.moa.claim.ClaimStatus.REPORTED, c.reportedAt = :now"
            + " where c.id = :claimId and c.buyer.id = :buyerId and c.status = com.moa.claim.ClaimStatus.WON"
            + " and c.deadlineAt > :now")
    int report(@Param("claimId") Long claimId, @Param("buyerId") Long buyerId, @Param("now") LocalDateTime now);

    // 판매자 [입금확인]. 시간 제한이 없다 (D-07). [입금 안 됨]과 같은 REPORTED 조건이라 둘 중 하나만 이긴다 (05 문서 AC-18)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Claim c set c.status = com.moa.claim.ClaimStatus.CONFIRMED, c.confirmedAt = :now"
            + " where c.id = :claimId and c.status = com.moa.claim.ClaimStatus.REPORTED")
    int confirm(@Param("claimId") Long claimId, @Param("now") LocalDateTime now);

    // 판매자 [입금 안 됨]
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Claim c set c.status = com.moa.claim.ClaimStatus.REJECTED, c.endedAt = :now"
            + " where c.id = :claimId and c.status = com.moa.claim.ClaimStatus.REPORTED")
    int reject(@Param("claimId") Long claimId, @Param("now") LocalDateTime now);
}
