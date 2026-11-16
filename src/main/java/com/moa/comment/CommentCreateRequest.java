package com.moa.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 내용만 받는다. 작성자는 세션, 시각은 서버 Clock (04 문서 AC-10)
public record CommentCreateRequest(@NotBlank @Size(max = Comment.MAX_CONTENT_LENGTH) String content) {
}
