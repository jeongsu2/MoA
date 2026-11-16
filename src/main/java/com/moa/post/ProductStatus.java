package com.moa.post;

// 수량은 항상 1개라 재고 대신 상태만 둔다 (D-12). RESERVED는 진행 중 당첨이 있을 때, SOLD는 입금 확인 뒤 (ERD v2 3장)
public enum ProductStatus {
    ON_SALE,
    RESERVED,
    SOLD
}
