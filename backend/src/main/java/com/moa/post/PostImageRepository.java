package com.moa.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

    // 영속성 컨텍스트는 비우지 않는다. 비우면 같은 트랜잭션에서 고친 글(SalePost)의 변경이 사라진다
    @Modifying(flushAutomatically = true)
    @Query("delete from PostImage i where i.post.id = :postId")
    int deleteByPostId(@Param("postId") Long postId);
}
