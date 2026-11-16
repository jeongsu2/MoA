package com.moa.post;

import java.time.LocalDateTime;

// 판매 오픈과 종료의 응답
public record PostStatusResponse(PostStatus status, LocalDateTime openedAt) {
}
