package com.moa.comment;

import com.moa.claim.Claim;
import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

// 당첨이 아니면 won=false, claim=null
public record CommentCreateResponse(Long commentId, CommentType type, Long productId, boolean won,
        ClaimSummary claim) {

    public record ClaimSummary(Long claimId, ClaimStatus status, LocalDateTime deadlineAt) {
    }

    static CommentCreateResponse of(Comment comment, Long productId, Claim claim) {
        if (claim == null) {
            return new CommentCreateResponse(comment.getId(), comment.getType(), productId, false, null);
        }
        return new CommentCreateResponse(comment.getId(), comment.getType(), productId, true,
                new ClaimSummary(claim.getId(), claim.getStatus(), claim.getDeadlineAt()));
    }
}
