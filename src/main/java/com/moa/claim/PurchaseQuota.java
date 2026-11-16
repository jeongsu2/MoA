package com.moa.claim;

import com.moa.post.SalePost;
import com.moa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 글별 1인 구매 수 카운터 (D-13). 값은 엔티티로 바꾸지 않고 PurchaseQuotaRepository의 조건부 UPDATE로만 바꾼다.
// 매번 COUNT(*)를 세면 동시 요청 두 개가 둘 다 4를 보고 6개가 통과한다
@Entity
@Table(name = "purchase_quota")
@IdClass(PurchaseQuotaId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseQuota {

    // 한 글 안에서 당첨 중(WON, REPORTED) + 확정(CONFIRMED) 합계 상한
    public static final int MAX_ACTIVE_COUNT = 5;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int activeCount;
}
