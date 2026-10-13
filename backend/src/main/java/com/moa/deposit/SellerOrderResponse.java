package com.moa.deposit;

import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

// 판매자가 통장과 대조하고 일괄 발송할 때 보는 한 줄 (05 문서 AC-21). 주소, 연락처는 신고 뒤에만 생긴다 (AC-22)
public record SellerOrderResponse(Long claimId, ClaimStatus status, String itemNo, String productName,
        String buyerNickname, String buyerRealName, int totalAmount, int shippingFee, String recipientName,
        String recipientPhone, String zipCode, String address1, String address2, String deliveryNote,
        LocalDateTime reportedAt, LocalDateTime confirmedAt) {
}
