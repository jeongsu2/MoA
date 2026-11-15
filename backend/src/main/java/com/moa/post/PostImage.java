package com.moa.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 파일이 아니라 저장소 키와 순서만 저장한다 (03 문서 AC-19). URL은 응답할 때 ImageStorage가 만든다
@Entity
@Table(name = "post_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private SalePost post;

    @Column(nullable = false, length = 100)
    private String imageKey;

    @Column(nullable = false)
    private int sortOrder;

    public PostImage(SalePost post, String imageKey, int sortOrder) {
        this.post = post;
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }
}
