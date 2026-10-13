package com.moa.claim;

import java.time.LocalDateTime;

// 당첨자 본인에게만 보이는 입금 정보 (05 문서 AC-1). 주문 정보(order)는 입금완료 신고(#30) 뒤에 붙는다
public record MyClaimView(Long claimId, Long productId, ClaimStatus status, LocalDateTime deadlineAt,
        String bankName, String accountNumber, String accountHolder, int price, int shippingFee,
        int remoteShippingFee) {
}
