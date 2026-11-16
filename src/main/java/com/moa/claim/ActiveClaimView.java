package com.moa.claim;

import java.time.LocalDateTime;

// 상품의 진행 중 당첨. buyerRealName은 판매자에게만 내보낸다 (ERD 5장)
public record ActiveClaimView(Long claimId, Long productId, Long commentId, String buyerNickname,
        String buyerRealName, ClaimStatus status, LocalDateTime deadlineAt) {
}
