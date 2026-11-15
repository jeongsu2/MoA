package com.moa.comment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 수정 API는 없다 (D-11). 잘못 쓰면 새 댓글을 단다
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // 로그인한 누구나 쓴다. 판매자도 다른 판매자 글에 주문할 수 있다 (D-34)
    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentCreateResponse createComment(@AuthenticationPrincipal Long userId, @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        return commentService.createComment(userId, postId, request.content());
    }
}
