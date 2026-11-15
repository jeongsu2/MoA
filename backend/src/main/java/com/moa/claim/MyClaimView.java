package com.moa.claim;

import java.time.LocalDateTime;

// 당첨자 본인에게만 보이는 입금 정보 (05 문서 AC-1). order로 시작하는 값은 입금완료 신고 뒤에만 있고 그 전에는 null
public record MyClaimView(Long claimId, Long productId, ClaimStatus status, LocalDateTime deadlineAt,
        String bankName, String accountNumber, String accountHolder, int price, int shippingFee,
        int remoteShippingFee, Integer orderTotalAmount, Integer orderPrice, Integer orderShippingFee,
        String orderZipCode, String orderAddress1) {

    public boolean hasOrder() {
        return orderTotalAmount != null;
    }
}
