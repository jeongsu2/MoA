package com.moa.comment;

// 차순위 후보. 아직 claim이 없는 ORDER 댓글과 그 작성자 (06 문서 AC-11)
public record OrderCandidate(Long commentId, Long userId) {
}
