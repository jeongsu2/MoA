package com.moa.comment;

// 한 테이블에 type으로 구분한다 (ERD v2). DEPOSIT_REPORT는 #30, SYSTEM은 감사 댓글에서 쓴다
public enum CommentType {
    NORMAL,
    ORDER,
    DEPOSIT_REPORT,
    SYSTEM
}
