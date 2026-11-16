package com.moa.post;

import com.moa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// status를 바꾸는 메서드는 일부러 없다. 전이는 SalePostRepository의 조건부 UPDATE에서만 일어난다
@Entity
@Table(name = "sale_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalePost {

    // 지금은 고정값이지만 글마다 저장한다. 나중에 값이 바뀌어도 과거 글의 입금 금액 근거가 남는다 (D-25)
    public static final int DEFAULT_SHIPPING_FEE = 3500;
    public static final int DEFAULT_REMOTE_SHIPPING_FEE = 6000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(nullable = false)
    private int shippingFee;

    @Column(nullable = false)
    private int remoteShippingFee;

    private LocalDateTime openedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SalePost draft(User seller, String title, String content, LocalDateTime now) {
        SalePost post = new SalePost();
        post.seller = seller;
        post.title = title;
        post.content = content;
        post.status = PostStatus.DRAFT;
        post.shippingFee = DEFAULT_SHIPPING_FEE;
        post.remoteShippingFee = DEFAULT_REMOTE_SHIPPING_FEE;
        post.createdAt = now;
        return post;
    }

    // 제목과 본문은 OPEN 뒤에도 고칠 수 있다 (03 문서 Q-1 A)
    public void changeText(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public boolean isDraft() {
        return status == PostStatus.DRAFT;
    }

    // 지연 로딩 프록시라도 id는 쿼리 없이 꺼낸다
    public boolean isWrittenBy(Long userId) {
        return seller.getId().equals(userId);
    }
}
