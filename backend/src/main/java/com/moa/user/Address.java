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

// 지금은 가입 때 기본 배송지 1개만 저장한다. 목록, 추가, 기본 변경은 #27
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
        Address address = new Address();
        address.user = user;
        address.recipientName = recipientName;
        address.recipientPhone = recipientPhone;
        address.zipCode = zipCode;
        address.address1 = address1;
        address.address2 = address2;
        address.deliveryNote = deliveryNote;
        address.isDefault = true;
        return address;
    }
}
