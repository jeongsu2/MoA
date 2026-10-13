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
}
