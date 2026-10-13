package com.moa.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 번호 문자열 정렬은 "10"이 "2"보다 앞서서, 판매자가 입력한 순서(id)로 보여 준다
    List<Product> findByPostIdOrderByIdAsc(Long postId);
}
