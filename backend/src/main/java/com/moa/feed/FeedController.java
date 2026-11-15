package com.moa.feed;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 프론트가 2초마다 부른다 (D-18 짧은 폴링). 처음엔 afterId=0, 다음엔 받은 마지막 댓글 id
@RestController
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    // 비로그인도 부른다. 그때는 principal이 Long이 아니라서 userId가 null로 들어온다
    @GetMapping("/api/posts/{postId}/feed")
    public FeedResponse getFeed(@AuthenticationPrincipal Long userId, @PathVariable Long postId,
            @RequestParam(defaultValue = "0") long afterId) {
        return feedService.getFeed(postId, userId, afterId);
    }
}
