package com.moa.feed;

import com.moa.comment.CommentView;
import com.moa.post.PostStatus;
import java.time.LocalDateTime;
import java.util.List;

// 폴링 한 번에 요청 하나 (04 문서 3장). 남은 시간은 serverTime과 deadlineAt으로 브라우저가 센다 (AC-20)
public record FeedResponse(LocalDateTime serverTime, PostStatus postStatus, List<FeedProductResponse> products,
        List<CommentView> comments, boolean hasMore, List<MyClaimResponse> myClaims) {
}
