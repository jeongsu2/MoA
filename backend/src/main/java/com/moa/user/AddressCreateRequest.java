package com.moa.user;

import com.moa.auth.SignupPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 주소 검색은 프론트가 하고 서버는 형식만 본다 (D-34). setDefault를 빼면 false
public record AddressCreateRequest(
        @NotBlank @Size(max = 20) String recipientName,
        @NotBlank @Pattern(regexp = SignupPatterns.PHONE, message = "숫자만 10~11자리, 01로 시작") String recipientPhone,
        @NotBlank @Pattern(regexp = SignupPatterns.ZIP_CODE, message = "숫자 5자리") String zipCode,
        @NotBlank @Size(max = 200) String address1,
        @Size(max = 200) String address2,
        @Size(max = 100) String deliveryNote,
        Boolean setDefault) {

    // 기본형 boolean이면 Jackson이 빠진 값을 400으로 거부해서 Boolean으로 받고 null은 false로 본다
    public boolean wantsDefault() {
        return Boolean.TRUE.equals(setDefault);
    }
}
