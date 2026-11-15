package com.moa.user;

import com.moa.auth.SignupPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerProfileRequest(
        @NotBlank @Size(max = 20) String bankName,
        @NotBlank @Pattern(regexp = SignupPatterns.ACCOUNT_NUMBER, message = "숫자와 - 6~30자") String accountNumber,
        @NotBlank @Size(max = 20) String accountHolder) {
}
