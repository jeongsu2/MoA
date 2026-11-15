package com.moa.feed;

import com.moa.post.ProductStatus;

// 진행 중 당첨이 없으면 claim은 null
public record FeedProductResponse(Long productId, String itemNo, ProductStatus status, FeedClaimResponse claim) {
}
