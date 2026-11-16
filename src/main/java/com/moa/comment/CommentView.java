package com.moa.comment;

import java.time.LocalDateTime;

// 피드에 보이는 댓글 한 줄. 당첨 표시는 여기 없다. 상품의 진행 중 당첨으로 그린다 (04 문서 AC-17)
public record CommentView(Long commentId, CommentType type, Long parentId, String nickname, String content,
        Long productId, LocalDateTime createdAt) {
}
