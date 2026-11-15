package com.moa.post;

public record ProductResponse(Long productId, String itemNo, String name, int price, ProductStatus status) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getItemNo(), product.getName(),
                product.getPrice(), product.getStatus());
    }
}
