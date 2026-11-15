package com.moa.deposit;

import com.moa.claim.ClaimStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByClaimId(Long claimId);

    // 신고한 순서대로. 입금 안 됨(REJECTED) 주문 행은 기록으로 남지만 목록에는 안 나온다
    @Query("select new com.moa.deposit.SellerOrderResponse(c.id, c.status, p.itemNo, p.name, u.nickname,"
            + " u.realName, o.totalAmount, o.shippingFee, o.recipientName, o.recipientPhone, o.zipCode, o.address1,"
            + " o.address2, o.deliveryNote, c.reportedAt, c.confirmedAt)"
            + " from PurchaseOrder o join o.claim c join c.product p join o.buyer u"
            + " where p.post.id = :postId and c.status in :statuses order by c.reportedAt asc, c.id asc")
    List<SellerOrderResponse> findSellerOrders(@Param("postId") Long postId,
            @Param("statuses") Collection<ClaimStatus> statuses);
}
