package com.moa.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 판매자는 세션에서 꺼낸다. sellerId, 배송비는 받지 않는다 (D-25). 상품 0개로 미리 써 둘 수 있다
public record PostCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull @Size(max = 5000) String content,
        @NotNull List<@NotBlank String> imageKeys,
        @NotNull List<@NotNull @Valid ProductRequest> products) {
}
