package com.moa.comment;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // comment (post_id, id) 인덱스로 "마지막 id 이후만" 읽는다 (ERD 4장).
    // 여기에 최근 몇 초 안에 만든 댓글을 겹쳐 읽는다. id는 INSERT 때, 보이는 시점은 COMMIT 때라서
    // 작은 id 댓글이 큰 id 댓글보다 늦게 커밋되면 id만으로는 영원히 놓친다. 중복은 프론트가 commentId로 거른다
    @Query("select new com.moa.comment.CommentView(c.id, c.type, c.parent.id, u.nickname, c.content,"
            + " c.product.id, c.createdAt)"
            + " from Comment c join c.user u where c.post.id = :postId"
            + " and (c.id > :afterId or c.createdAt >= :recentSince) order by c.id asc")
    List<CommentView> findFeedComments(@Param("postId") Long postId, @Param("afterId") long afterId,
            @Param("recentSince") LocalDateTime recentSince, Pageable pageable);

    // 차순위 후보 (06 문서 AC-11, AC-12, Q-1 A). comment (product_id, type, id) 인덱스를 id 순서로 읽는다.
    // claim이 있는 댓글은 한 번 당첨된 댓글이라 빼고 (claim.comment_id 유니크), 그 글에서 이미 5개인 작성자도 뺀다.
    // returningBuyerId는 아직 quota를 돌려받지 않은 끝난 구매자. 그 사람 수는 1을 빼고 본다 (돌려받을 몫).
    // 락 없이 읽는 값이라 고르는 데만 쓰고, 실제로 한 칸 얻는지는 quota 조건부 UPDATE로 다시 판단한다
    @Query("select new com.moa.comment.OrderCandidate(c.id, c.user.id) from Comment c"
            + " where c.product.id = :productId and c.type = com.moa.comment.CommentType.ORDER and c.id > :afterId"
            + " and not exists (select 1 from Claim cl where cl.comment = c)"
            + " and coalesce((select q.activeCount from PurchaseQuota q"
            + " where q.post.id = :postId and q.user.id = c.user.id), 0)"
            + " - (case when c.user.id = :returningBuyerId then 1 else 0 end) < :max"
            + " order by c.id asc")
    List<OrderCandidate> findOrderCandidates(@Param("productId") Long productId, @Param("postId") Long postId,
            @Param("afterId") long afterId, @Param("returningBuyerId") long returningBuyerId, @Param("max") int max,
            Pageable pageable);
}
