package com.moa.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseQuotaRepository extends JpaRepository<PurchaseQuota, PurchaseQuotaId> {

    // 첫 행 만들기. 조회 후 INSERT는 동시 첫 댓글에서 중복 키 500이 나서 한 문장으로 만든다.
    // 이미 있으면 값은 그대로 두고 그 행을 잠근다 (claim-implementation-notes 6번)
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into purchase_quota (post_id, user_id, active_count) values (:postId, :userId, 0)"
            + " on duplicate key update active_count = active_count", nativeQuery = true)
    int createIfAbsent(@Param("postId") Long postId, @Param("userId") Long userId);

    // 조건부 UPDATE. 1이면 한 칸 얻음, 0이면 이미 상한이라 당첨하지 않는다 (04 문서 AC-12)
    @Modifying(flushAutomatically = true)
    @Query(value = "update purchase_quota set active_count = active_count + 1"
            + " where post_id = :postId and user_id = :userId and active_count < :max", nativeQuery = true)
    int increase(@Param("postId") Long postId, @Param("userId") Long userId, @Param("max") int max);
}
