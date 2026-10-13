package com.moa.post;

// 수량은 항상 1개라 재고 대신 상태만 둔다 (D-12). RESERVED, SOLD는 당첨 흐름(#29)에서 쓴다
public enum ProductStatus {
    ON_SALE,
    RESERVED,
    SOLD
}
