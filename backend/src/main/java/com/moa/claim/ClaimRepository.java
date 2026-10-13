package com.moa.claim;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
