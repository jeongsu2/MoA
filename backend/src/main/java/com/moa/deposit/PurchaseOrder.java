package com.moa.deposit;

import com.moa.claim.Claim;
import com.moa.user.Address;
import com.moa.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// ERD v2의 orders. 클래스 이름은 JPQL 예약어 ORDER와 겹치지 않게 PurchaseOrder로 둔다.
// 입금완료를 누른 순간의 기본 배송지와 금액을 복사해 둔다 (D-24). address_id를 참조하지 않아서
// 나중에 배송지를 고치거나 기본을 바꿔도 이 주문의 주소는 안 바뀐다. 바꾸는 메서드도 없다
@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
            // 당첨 하나에 주문 하나. 신고 연타의 최종 방어선 (05 문서 AC-6)
            @UniqueConstraint(name = PurchaseOrder.UK_CLAIM, columnNames = "claim_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

    public static final String UK_CLAIM = "uk_orders_claim";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(nullable = false, length = 20)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 5)
    private String zipCode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(length = 100)
    private String deliveryNote;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int shippingFee;

    // 구매자가 입금해야 할 금액 = price + shippingFee (D-25). 판매자가 통장과 대조하는 값
    @Column(nullable = false)
    private int totalAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PurchaseOrder snapshot(Claim claim, User buyer, Address address, int price, int shippingFee,
            LocalDateTime now) {
        PurchaseOrder order = new PurchaseOrder();
        order.claim = claim;
        order.buyer = buyer;
        order.recipientName = address.getRecipientName();
        order.recipientPhone = address.getRecipientPhone();
        order.zipCode = address.getZipCode();
        order.address1 = address.getAddress1();
        order.address2 = address.getAddress2();
        order.deliveryNote = address.getDeliveryNote();
        order.price = price;
        order.shippingFee = shippingFee;
        order.totalAmount = price + shippingFee;
        order.createdAt = now;
        return order;
    }
}
