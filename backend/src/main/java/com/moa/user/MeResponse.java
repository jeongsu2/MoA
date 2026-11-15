package com.moa.user;

import com.fasterxml.jackson.annotation.JsonInclude;

// 비밀번호 해시는 응답에 넣지 않는다. sellerProfile은 판매자만 나간다
public record MeResponse(Long userId, String loginId, Role role, String nickname, String realName,
        String phone, AddressResponse defaultAddress,
        @JsonInclude(JsonInclude.Include.NON_NULL) SellerProfileResponse sellerProfile) {
}
