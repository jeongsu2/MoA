package com.moa.claim;

import com.moa.comment.Comment;
import com.moa.post.Product;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 당첨 한 번 = 한 행 (D-20). status를 바꾸는 메서드는 없다. 전이는 조건부 UPDATE로만 한다
@Entity
@Table(
        name = "claim",
        uniqueConstraints = {
            // 한 댓글은 한 번만 당첨된다. 취소된 사람의 예전 댓글이 다시 후보가 되지 않게 하는 최종 방어선 (ERD 5장)
            @UniqueConstraint(name = Claim.UK_COMMENT, columnNames = "comment_id")
        },
        indexes = {
            // 만료 스케줄러 (#31)
            @Index(name = "idx_claim_status_deadline", columnList = "status, deadline_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim {

    public static final String UK_COMMENT = "uk_claim_comment";
    public static final Duration DEPOSIT_DEADLINE = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(nullable = false)
    private LocalDateTime wonAt;

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    private LocalDateTime reportedAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime endedAt;

    // 시각은 서버 Clock 값만 받는다. 클라이언트가 보낸 시각은 쓰지 않는다 (04 문서 AC-10)
    public static Claim won(Product product, Comment comment, User buyer, LocalDateTime now) {
        Claim claim = new Claim();
        claim.product = product;
        claim.comment = comment;
        claim.buyer = buyer;
        claim.status = ClaimStatus.WON;
        claim.wonAt = now;
        claim.deadlineAt = now.plus(DEPOSIT_DEADLINE);
        return claim;
    }
}
