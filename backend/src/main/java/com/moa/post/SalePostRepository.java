package com.moa.post;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalePostRepository extends JpaRepository<SalePost, Long> {

    // 최신순 = id 역순. 상품 수는 글마다 따로 묻지 않고 한 쿼리로 같이 센다
    @Query("select new com.moa.post.MyPostResponse(p.id, p.title, p.status, p.openedAt,"
            + " (select count(pr) from Product pr where pr.post = p))"
            + " from SalePost p where p.seller.id = :sellerId order by p.id desc")
    List<MyPostResponse> findMyPosts(@Param("sellerId") Long sellerId);

    // SELECT ... FOR UPDATE. 수정과 오픈이 동시에 와도 한 줄로 선다.
    // 오픈이 먼저 커밋되면 수정은 OPEN을 보고 상품 변경을 거부한다. 내 글이 아니면 잠그지 않고 빈 결과
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SalePost p where p.id = :postId and p.seller.id = :sellerId")
    Optional<SalePost> findMyPostForUpdate(@Param("postId") Long postId, @Param("sellerId") Long sellerId);
}
