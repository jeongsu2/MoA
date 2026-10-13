package com.moa.deposit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// 요청 본문이 없다. 누가 누르는지는 세션, 무엇을 누르는지는 경로로 정한다.
// 금액과 주소를 클라이언트에서 받지 않아 위조할 수 없다 (05 문서 3장)
@RestController
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/api/claims/{claimId}/report")
    public ReportResponse report(@AuthenticationPrincipal Long userId, @PathVariable Long claimId) {
        return depositService.report(userId, claimId);
    }

    @PostMapping("/api/claims/{claimId}/confirm")
    public ConfirmResponse confirm(@AuthenticationPrincipal Long userId, @PathVariable Long claimId) {
        return depositService.confirm(userId, claimId);
    }

    @PostMapping("/api/claims/{claimId}/reject")
    public RejectResponse reject(@AuthenticationPrincipal Long userId, @PathVariable Long claimId) {
        return depositService.reject(userId, claimId);
    }
}
