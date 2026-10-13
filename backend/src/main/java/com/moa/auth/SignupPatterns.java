package com.moa.auth;

// 가입 입력값 형식. 전화번호와 우편번호는 형식만 본다 (D-34)
final class SignupPatterns {

    static final String LOGIN_ID = "^[a-z0-9]{4,20}$";
    // BCrypt는 72바이트까지만 쓰므로 한 글자 1바이트인 영문, 숫자, 특수문자만 받는다
    static final String PASSWORD = "^[!-~]{8,64}$";
    static final String PHONE = "^01[0-9]{8,9}$";
    static final String ZIP_CODE = "^[0-9]{5}$";
    static final String ACCOUNT_NUMBER = "^[0-9-]{6,30}$";

    private SignupPatterns() {
    }
}
