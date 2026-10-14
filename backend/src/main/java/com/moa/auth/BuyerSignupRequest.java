package com.moa.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// role, status, 동의 시각은 받지 않는다. 본문에 끼워 보내도 무시된다
public record BuyerSignupRequest(
        @NotBlank @Pattern(regexp = SignupPatterns.LOGIN_ID, message = "영문 소문자, 숫자 4~20자") String loginId,
        @NotNull @Pattern(regexp = SignupPatterns.PASSWORD, message = "영문, 숫자, 특수문자 8~64자") String password,
        @NotBlank @Size(min = 2, max = 20)
        @Pattern(regexp = SignupPatterns.NICKNAME, message = "앞뒤 공백 없이") String nickname,
        @NotBlank @Size(max = 50) String realName,
        @NotBlank @Pattern(regexp = SignupPatterns.PHONE, message = "숫자만 10~11자리, 01로 시작") String phone,
        @NotNull @Valid AddressRequest address,
        @NotNull @AssertTrue Boolean privacyAgreed,
        @NotNull @AssertTrue Boolean thirdPartyAgreed) {
}
