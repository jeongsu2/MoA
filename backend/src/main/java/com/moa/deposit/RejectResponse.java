package com.moa.deposit;

import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

// nextClaim은 차순위 승계(#31)가 들어오면 채운다. 지금은 늘 null
public record RejectResponse(Long claimId, ClaimStatus status, NextClaim nextClaim) {

    public record NextClaim(Long claimId, Long commentId, String buyerNickname, LocalDateTime deadlineAt) {
    }
}
