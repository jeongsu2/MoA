package com.moa.feed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moa.claim.ClaimStatus;
import com.moa.claim.MyClaimView;
import com.moa.deposit.OrderSummary;
import java.time.LocalDateTime;

// 피드의 myClaims 한 줄. order는 입금완료 신고 뒤에만 있고, 그 전에는 키 자체가 없다 (05 문서 3장)
public record MyClaimResponse(Long claimId, Long productId, ClaimStatus status, LocalDateTime deadlineAt,
        String bankName, String accountNumber, String accountHolder, int price, int shippingFee,
        int remoteShippingFee, @JsonInclude(JsonInclude.Include.NON_NULL) OrderSummary order) {

    static MyClaimResponse from(MyClaimView view) {
        OrderSummary order = view.hasOrder()
                ? new OrderSummary(view.orderTotalAmount(), view.orderPrice(), view.orderShippingFee(),
                        view.orderZipCode(), view.orderAddress1())
                : null;
        return new MyClaimResponse(view.claimId(), view.productId(), view.status(), view.deadlineAt(),
                view.bankName(), view.accountNumber(), view.accountHolder(), view.price(), view.shippingFee(),
                view.remoteShippingFee(), order);
    }
}
