package com.moa.post;

import java.time.LocalDateTime;

public record MyPostResponse(Long postId, String title, PostStatus status, LocalDateTime openedAt,
        long productCount) {
}
