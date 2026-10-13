package com.moa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자당 기본(is_default=true)은 항상 정확히 1개. 바꾸는 요청은 AddressService가 users 행 락으로 줄 세운다 (D-34)
@Entity
@Table(name = "address", indexes = @Index(name = "idx_address_user_default", columnList = "user_id, is_default"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    // 앞자리 0이 사라지지 않게 문자열로 저장 (예: 06236)
    @Column(nullable = false, length = 5)
    private String zipCode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(length = 100)
    private String deliveryNote;

    @Column(nullable = false)
    private boolean isDefault;

    public static Address defaultOf(User user, String recipientName, String recipientPhone,
            String zipCode, String address1, String address2, String deliveryNote) {
        return create(user, recipientName, recipientPhone, zipCode, address1, address2, deliveryNote, true);
    }

    public static Address create(User user, String recipientName, String recipientPhone, String zipCode,
            String address1, String address2, String deliveryNote, boolean isDefault) {
        Address address = new Address();
        address.user = user;
        address.recipientName = recipientName;
        address.recipientPhone = recipientPhone;
        address.zipCode = zipCode;
        address.address1 = address1;
        address.address2 = address2;
        address.deliveryNote = deliveryNote;
        address.isDefault = isDefault;
        return address;
    }

    // 이미 생긴 주문은 주소를 스냅샷으로 따로 갖고 있어서, 여기서 고쳐도 주문 주소는 안 바뀐다
    public void change(String recipientName, String recipientPhone, String zipCode,
            String address1, String address2, String deliveryNote) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.deliveryNote = deliveryNote;
    }
}
