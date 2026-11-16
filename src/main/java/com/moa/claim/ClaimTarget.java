package com.moa.claim;

// 입금 동작(신고, 확인, 입금 안 됨) 전에 권한과 잠글 상품을 알려고 읽는 값. 모두 claim이 생긴 뒤 안 바뀌는 값이라
// 락 전에 읽어도 된다. 상태는 여기 없다. 상태는 락을 잡은 뒤 조건부 UPDATE로만 판단한다
public record ClaimTarget(Long claimId, Long productId, Long postId, Long sellerId, Long buyerId,
        Long commentId) {
}
