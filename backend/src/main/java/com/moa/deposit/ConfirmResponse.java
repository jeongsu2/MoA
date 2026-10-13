package com.moa.deposit;

import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

public record ConfirmResponse(Long claimId, ClaimStatus status, LocalDateTime confirmedAt) {
}
