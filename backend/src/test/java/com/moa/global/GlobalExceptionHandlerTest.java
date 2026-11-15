package com.moa.global;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("데드락(CannotAcquireLockException)은 500이 아니라 409 LOCK_CONFLICT 다시 시도")
    void deadlockIsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleLockFailure(new CannotAcquireLockException("Deadlock found when trying to get lock"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("LOCK_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해주세요");
    }
}
