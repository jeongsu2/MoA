package com.moa.auth;

// 가입 입력값 형식. 전화번호와 우편번호는 형식만 본다 (D-34)
public final class SignupPatterns {

    public static final String LOGIN_ID = "^[a-z0-9]{4,20}$";
    // BCrypt는 72바이트까지만 쓰므로 한 글자 1바이트인 영문, 숫자, 특수문자만 받는다
    public static final String PASSWORD = "^[!-~]{8,64}$";
    // 앞뒤 공백 금지. MySQL 콜레이션이 공백을 다른 글자로 봐서 " 홍길동"이 유니크를 통과한다 (D-35)
    public static final String NICKNAME = "^\\S(.*\\S)?$";
    public static final String PHONE = "^01[0-9]{8,9}$";
    public static final String ZIP_CODE = "^[0-9]{5}$";
    public static final String ACCOUNT_NUMBER = "^[0-9-]{6,30}$";

    private SignupPatterns() {
    }
}
