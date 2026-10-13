package com.moa.global;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

// fieldErrors는 입력값 검증 실패일 때만 나간다 (필드 이름 -> 이유)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null);
    }
}
