package com.moa.deposit;

import com.moa.claim.ClaimStatus;
import java.time.LocalDateTime;

// 연타로 다시 눌러도 같은 주문이 온다. status는 그 순간의 상태라 판매자가 이미 확인했으면 CONFIRMED
public record ReportResponse(Long claimId, ClaimStatus status, LocalDateTime reportedAt, OrderSummary order) {
}
