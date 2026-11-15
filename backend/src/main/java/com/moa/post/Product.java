package com.moa.post;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 수량 컬럼이 없다. 상품 하나는 항상 1개 (D-12)
@Entity
@Table(
        name = "product",
        uniqueConstraints = {
            // 같은 글 안 번호 중복의 최종 방어선. 댓글 "1번 주세요"가 상품 하나만 가리키게 한다
            @UniqueConstraint(name = Product.UK_POST_ITEM_NO, columnNames = {"post_id", "item_no"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    public static final String UK_POST_ITEM_NO = "uk_product_post_item_no";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;

    // "1", "1-2"처럼 판매자가 자유롭게 쓴다 (ERD v2)
    @Column(nullable = false, length = 20)
    private String itemNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    public Product(SalePost post, String itemNo, String name, int price) {
        this.post = post;
        this.itemNo = itemNo;
        this.name = name;
        this.price = price;
        this.status = ProductStatus.ON_SALE;
    }
}
