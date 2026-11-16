package com.moa.deposit;

// 구매자에게 보여 주는 주문 요약 (05 문서 AC-11). 받는 사람과 연락처는 판매자 주문 목록에서만
public record OrderSummary(int totalAmount, int price, int shippingFee, String zipCode, String address1) {

    static OrderSummary from(PurchaseOrder order) {
        return new OrderSummary(order.getTotalAmount(), order.getPrice(), order.getShippingFee(),
                order.getZipCode(), order.getAddress1());
    }
}
