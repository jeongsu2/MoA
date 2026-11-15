package com.moa.post;

// 댓글 판정에 필요한 값만 읽는다. 엔티티로 읽으면 같은 트랜잭션의 FOR UPDATE 조회가 예전 상태를 돌려줄 수 있다
public record ProductKey(Long productId, String itemNo, String name) {
}
