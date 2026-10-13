package com.moa.comment;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // comment (post_id, id) 인덱스로 "마지막 id 이후만" 읽는다 (ERD 4장)
    @Query("select new com.moa.comment.CommentView(c.id, c.type, c.parent.id, u.nickname, c.content,"
            + " c.product.id, c.createdAt)"
            + " from Comment c join c.user u where c.post.id = :postId and c.id > :afterId order by c.id asc")
    List<CommentView> findFeedComments(@Param("postId") Long postId, @Param("afterId") long afterId,
            Pageable pageable);
}
