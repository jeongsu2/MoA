package com.moa.claim;

import java.util.List;

// WON -> REPORTED -> CONFIRMED, 또는 EXPIRED, REJECTED로 끝난다 (ERD v2 3장)
public enum ClaimStatus {
    WON,
    REPORTED,
    CONFIRMED,
    EXPIRED,
    REJECTED;

    // 상품을 잡고 있는 상태. 상품마다 이 상태의 claim은 0개 또는 1개
    public static final List<ClaimStatus> IN_PROGRESS = List.of(WON, REPORTED);
}
