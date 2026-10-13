package com.moa.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// products를 빼면(null) 상품은 그대로 둔다. 보내면 DRAFT일 때만 통째로 바꾼다 (03 문서 Q-1 A)
public record PostUpdateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull @Size(max = 5000) String content,
        @NotNull List<@NotBlank String> imageKeys,
        List<@NotNull @Valid ProductRequest> products) {
}
