package com.moa.comment;

import com.moa.post.Product;
import com.moa.post.SalePost;
import com.moa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 내용을 바꾸는 메서드가 없다. 수정으로 오타를 정답으로 바꿔 순서를 가로채지 못하게 한다 (D-11)
@Entity
@Table(
        name = "comment",
        indexes = {
            // 피드 폴링 "마지막 id 이후만" (D-18)
            @Index(name = "idx_comment_post_id", columnList = "post_id, id"),
            // 차순위 찾기 (#31)
            @Index(name = "idx_comment_product_type_id", columnList = "product_id, type, id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 지금은 SYSTEM 감사 댓글만 쓴다. 사용자 대댓글은 범위 밖
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentType type;

    // ORDER일 때만 매칭된 상품
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 삭제 API는 아직 없다 (04 문서 4장)
    private LocalDateTime deletedAt;

    public static Comment normal(SalePost post, User user, String content, LocalDateTime now) {
        return create(post, user, CommentType.NORMAL, null, content, now);
    }

    public static Comment order(SalePost post, User user, Product product, String content, LocalDateTime now) {
        return create(post, user, CommentType.ORDER, product, content, now);
    }

    private static Comment create(SalePost post, User user, CommentType type, Product product, String content,
            LocalDateTime now) {
        Comment comment = new Comment();
        comment.post = post;
        comment.user = user;
        comment.type = type;
        comment.product = product;
        comment.content = content;
        comment.createdAt = now;
        return comment;
    }
}
