package com.moa.post;

import com.moa.image.ImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 판매자 역할 확인은 SecurityConfig가 한다. 남의 글은 서비스에서 없는 글과 같은 404로 막는다
@RestController
public class PostController {

    private final PostService postService;
    private final ImageService imageService;

    public PostController(PostService postService, ImageService imageService) {
        this.postService = postService;
        this.imageService = imageService;
    }

    @PostMapping("/api/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostCreateResponse createPost(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody PostCreateRequest request) {
        imageService.requireUploaded(request.imageKeys());
        return postService.createPost(userId, request);
    }
}
