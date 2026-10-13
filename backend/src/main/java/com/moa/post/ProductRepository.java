package com.moa.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
