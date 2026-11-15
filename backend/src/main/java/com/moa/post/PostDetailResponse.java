package com.moa.post;

import java.time.LocalDateTime;
import java.util.List;

// 판매자 계좌와 실명은 넣지 않는다. 계좌는 당첨자에게만 보여 준다 (03 문서 AC-15, 05 문서)
public record PostDetailResponse(Long postId, String title, String content, PostStatus status,
        LocalDateTime openedAt, String sellerNickname, List<PostImageResponse> images,
        List<ProductResponse> products, int shippingFee, int remoteShippingFee) {
}
