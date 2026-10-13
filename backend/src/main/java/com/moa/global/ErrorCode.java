package com.moa.global;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 틀렸습니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
    // 남의 배송지와 없는 배송지는 같은 응답 (id가 있는지 드러내지 않음, D-34)
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다"),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "배송지는 최대 10개까지 저장할 수 있습니다"),
    DEFAULT_ADDRESS_NOT_DELETABLE(HttpStatus.CONFLICT, "기본 배송지는 삭제할 수 없습니다. 다른 배송지를 기본으로 바꾼 뒤 삭제해주세요"),
    IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, webp 이미지만 올릴 수 있습니다"),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 파일이 너무 큽니다"),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다"),
    IMAGE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "먼저 올리지 않은 이미지가 있습니다"),
    IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "글 하나에 올릴 수 있는 이미지 수를 넘었습니다"),
    DUPLICATE_ITEM_NO(HttpStatus.BAD_REQUEST, "같은 글 안에서 상품 번호가 겹칩니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
