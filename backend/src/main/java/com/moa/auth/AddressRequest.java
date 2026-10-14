package com.moa.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 20) String recipientName,
        @NotBlank @Pattern(regexp = SignupPatterns.PHONE, message = "숫자만 10~11자리, 01로 시작") String recipientPhone,
        @NotBlank @Pattern(regexp = SignupPatterns.ZIP_CODE, message = "숫자 5자리") String zipCode,
        @NotBlank @Size(max = 200) String address1,
        @Size(max = 200) String address2,
        @Size(max = 100) String deliveryNote) {
}
