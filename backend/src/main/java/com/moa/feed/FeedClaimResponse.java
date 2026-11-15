package com.moa.feed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moa.claim.ActiveClaimView;
import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

// 판매자가 아니면 buyerRealName 키 자체가 응답에 없다 (04 문서 AC-18)
public record FeedClaimResponse(Long claimId, Long commentId, String buyerNickname,
        @JsonInclude(JsonInclude.Include.NON_NULL) String buyerRealName, ClaimStatus status,
        LocalDateTime deadlineAt) {

    static FeedClaimResponse of(ActiveClaimView view, boolean showRealName) {
        return new FeedClaimResponse(view.claimId(), view.commentId(), view.buyerNickname(),
                showRealName ? view.buyerRealName() : null, view.status(), view.deadlineAt());
    }
}
