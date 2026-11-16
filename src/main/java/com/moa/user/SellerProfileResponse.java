package com.moa.user;

public record SellerProfileResponse(String bankName, String accountNumber, String accountHolder) {

    static SellerProfileResponse from(SellerProfile sellerProfile) {
        return new SellerProfileResponse(sellerProfile.getBankName(), sellerProfile.getAccountNumber(),
                sellerProfile.getAccountHolder());
    }
}
