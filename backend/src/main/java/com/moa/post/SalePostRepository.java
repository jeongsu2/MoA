package com.moa.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalePostRepository extends JpaRepository<SalePost, Long> {

    // 최신순 = id 역순. 상품 수는 글마다 따로 묻지 않고 한 쿼리로 같이 센다
    @Query("select new com.moa.post.MyPostResponse(p.id, p.title, p.status, p.openedAt,"
            + " (select count(pr) from Product pr where pr.post = p))"
            + " from SalePost p where p.seller.id = :sellerId order by p.id desc")
    List<MyPostResponse> findMyPosts(@Param("sellerId") Long sellerId);
}
