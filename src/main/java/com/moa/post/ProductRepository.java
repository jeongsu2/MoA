package com.moa.post;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 번호 문자열 정렬은 "10"이 "2"보다 앞서서, 판매자가 입력한 순서(id)로 보여 준다
    List<Product> findByPostIdOrderByIdAsc(Long postId);

    // 바로 DB에서 지운다. 엔티티 remove는 INSERT 뒤에 실행돼서 같은 번호를 다시 넣으면 유니크에 걸린다
    @Modifying(flushAutomatically = true)
    @Query("delete from Product p where p.post.id = :postId")
    int deleteByPostId(@Param("postId") Long postId);

    @Query("select new com.moa.post.ProductKey(p.id, p.itemNo, p.name) from Product p where p.post.id = :postId")
    List<ProductKey> findKeysByPostId(@Param("postId") Long postId);

    // SELECT ... FOR UPDATE. 같은 상품의 당첨 판정을 한 줄로 세운다. 락을 잡은 순서가 곧 당첨 순서다 (D-22).
    // 앞 요청이 커밋한 상태를 다시 읽으므로 진 요청은 RESERVED를 본다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

    // 진행 중 당첨 연결. 락을 잡고 부르므로 늘 1이어야 하고, 0이면 상품당 진행 중 claim 2개를 막는 마지막 방어선이 걸린 것 (D-20)
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.status = com.moa.post.ProductStatus.RESERVED, p.currentClaimId = :claimId"
            + " where p.id = :productId and p.status = com.moa.post.ProductStatus.ON_SALE"
            + " and p.currentClaimId is null")
    int reserve(@Param("productId") Long productId, @Param("claimId") Long claimId);

    // 입금확인. 확정은 진행 중이 아니라서 current_claim_id를 비운다 (ERD 3장, 04 테스트 계획 I-2)
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.status = com.moa.post.ProductStatus.SOLD, p.currentClaimId = null"
            + " where p.id = :productId and p.status = com.moa.post.ProductStatus.RESERVED"
            + " and p.currentClaimId = :claimId")
    int markSold(@Param("productId") Long productId, @Param("claimId") Long claimId);

    // 끝난 claim이 잡고 있던 상품을 판매 중으로 되돌린다. 다른 claim을 가리키면 건드리지 않는다
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.status = com.moa.post.ProductStatus.ON_SALE, p.currentClaimId = null"
            + " where p.id = :productId and p.status = com.moa.post.ProductStatus.RESERVED"
            + " and p.currentClaimId = :claimId")
    int release(@Param("productId") Long productId, @Param("claimId") Long claimId);

    // 차순위 승계. 끝난 claim을 가리킬 때만 새 claim으로 바꾼다. 상품은 RESERVED 그대로 (06 문서 AC-13, AC-16)
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.currentClaimId = :newClaimId"
            + " where p.id = :productId and p.status = com.moa.post.ProductStatus.RESERVED"
            + " and p.currentClaimId = :endedClaimId")
    int handOver(@Param("productId") Long productId, @Param("endedClaimId") Long endedClaimId,
            @Param("newClaimId") Long newClaimId);
}
