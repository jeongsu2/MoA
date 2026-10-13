package com.moa.deposit;

import com.moa.claim.Claim;
import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

// nextClaim은 차순위 승계로 새로 당첨된 claim. 후보가 없거나 글이 닫혔으면 null (06 문서 AC-14, AC-17)
public record RejectResponse(Long claimId, ClaimStatus status, NextClaim nextClaim) {

    public record NextClaim(Long claimId, Long commentId, String buyerNickname, LocalDateTime deadlineAt) {

        // 트랜잭션 안에서 부른다. buyer는 프록시라 닉네임을 읽을 때 한 번 조회한다
        static NextClaim from(Claim claim) {
            return new NextClaim(claim.getId(), claim.getComment().getId(), claim.getBuyer().getNickname(),
                    claim.getDeadlineAt());
        }
    }
}
