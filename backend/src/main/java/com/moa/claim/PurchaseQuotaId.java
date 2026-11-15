package com.moa.claim;

import java.io.Serializable;

// purchase_quota의 복합 키 (post_id, user_id)
public record PurchaseQuotaId(Long post, Long user) implements Serializable {
}
