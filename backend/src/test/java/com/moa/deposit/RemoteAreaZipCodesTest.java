package com.moa.deposit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// A-03 도서산간 판별 단위 테스트 (05 테스트 계획 2장)
class RemoteAreaZipCodesTest {

    private final RemoteAreaZipCodes remoteAreaZipCodes = new RemoteAreaZipCodes();

    @Test
    @DisplayName("A-03 제주 범위 양 끝과 안쪽은 산간")
    void jejuIsRemote() {
        assertThat(remoteAreaZipCodes.isRemote("63000")).isTrue();
        assertThat(remoteAreaZipCodes.isRemote("63309")).isTrue();
        assertThat(remoteAreaZipCodes.isRemote("63644")).isTrue();
        assertThat(remoteAreaZipCodes.isRemote("63645")).isFalse();
        assertThat(remoteAreaZipCodes.isRemote("62999")).isFalse();
    }

    @Test
    @DisplayName("A-03 목록의 다른 도서산간 우편번호(울릉)도 산간")
    void listedIslandIsRemote() {
        assertThat(remoteAreaZipCodes.isRemote("40200")).isTrue();
        assertThat(remoteAreaZipCodes.isRemote("40240")).isTrue();
    }

    @Test
    @DisplayName("A-03 서울과 앞자리 0인 우편번호는 산간이 아니고, 0이 사라지지 않는다")
    void seoulIsNotRemote() {
        assertThat(remoteAreaZipCodes.isRemote("06236")).isFalse();
        // 숫자로 바꿔 비교하면 "06300"이 6300이 되어 범위 판정이 흔들린다
        RemoteAreaZipCodes leadingZero = new RemoteAreaZipCodes(List.of("06300,06399"));
        assertThat(leadingZero.isRemote("06350")).isTrue();
        assertThat(leadingZero.isRemote("6350")).isFalse();
    }

    @Test
    @DisplayName("A-03 우편번호 형식이 아니면 산간이 아니다")
    void invalidZipCodeIsNotRemote() {
        assertThat(remoteAreaZipCodes.isRemote(null)).isFalse();
        assertThat(remoteAreaZipCodes.isRemote("")).isFalse();
        assertThat(remoteAreaZipCodes.isRemote("6300a")).isFalse();
    }

    @Test
    @DisplayName("목록 줄이 틀리면 시작할 때 실패한다")
    void brokenListFailsFast() {
        assertThatThrownBy(() -> new RemoteAreaZipCodes(List.of("63000")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RemoteAreaZipCodes(List.of("63644,63000")))
                .isInstanceOf(IllegalStateException.class);
    }
}
