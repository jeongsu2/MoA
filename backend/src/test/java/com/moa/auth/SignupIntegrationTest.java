package com.moa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
class SignupIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("A-01 구매자 가입: users(BUYER, ACTIVE)와 기본 배송지 1개가 생긴다")
    void signupBuyer() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/signup/buyer", buyerBody("buyer01", "구매자1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.role").value("BUYER"));

        Map<String, Object> user = jdbcTemplate.queryForMap("select * from users where login_id = 'buyer01'");
        assertThat(user.get("role")).isEqualTo("BUYER");
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        assertThat(user.get("privacy_agreed_at")).isNotNull();
        assertThat(user.get("third_party_agreed_at")).isNotNull();
        assertThat(countRows("address")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select is_default from address", Boolean.class)).isTrue();
        assertThat(countRows("seller_profile")).isZero();
    }

    @Test
    @DisplayName("A-02 판매자 가입: users(SELLER), seller_profile, 기본 배송지가 같이 생긴다")
    void signupSeller() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/signup/seller", sellerBody("seller01", "판매자1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SELLER"));

        assertThat(jdbcTemplate.queryForObject("select role from users", String.class)).isEqualTo("SELLER");
        assertThat(countRows("seller_profile")).isEqualTo(1);
        assertThat(countRows("address")).isEqualTo(1);
    }

    @Test
    @DisplayName("A-03 판매자 가입에 계좌가 빠지면 400이고 행이 안 생긴다")
    void signupSellerWithoutAccount() throws Exception {
        Map<String, Object> body = sellerBody("seller01", "판매자1");
        body.remove("bankName");
        body.remove("accountNumber");
        body.remove("accountHolder");

        mockMvc.perform(jsonPost("/api/auth/signup/seller", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bankName").exists())
                .andExpect(jsonPath("$.fieldErrors.accountNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.accountHolder").exists());

        assertThat(countRows("users")).isZero();
        assertThat(countRows("address")).isZero();
    }

    @Test
    @DisplayName("A-05 구매자 가입 본문에 role=SELLER, status를 끼워 보내도 무시된다")
    void signupBuyerIgnoresRoleInBody() throws Exception {
        Map<String, Object> body = buyerBody("buyer01", "구매자1");
        body.put("role", "SELLER");
        body.put("status", "SUSPENDED");
        body.put("privacyAgreedAt", "2000-01-01T00:00:00");

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        mockMvc.perform(jsonPost("/api/auth/signup/buyer", body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("BUYER"));

        Map<String, Object> user = jdbcTemplate.queryForMap("select role, status from users");
        assertThat(user.get("role")).isEqualTo("BUYER");
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        LocalDateTime agreedAt = jdbcTemplate.queryForObject("select privacy_agreed_at from users", LocalDateTime.class);
        assertThat(agreedAt).isAfter(before);
        assertThat(countRows("seller_profile")).isZero();
    }

    @ParameterizedTest(name = "privacy={0}, thirdParty={1}")
    @CsvSource({"true, false", "false, true", "false, false"})
    @DisplayName("A-07 동의를 하나라도 안 하면 400이고 users, address 행이 안 생긴다")
    void signupWithoutAgreement(boolean privacyAgreed, boolean thirdPartyAgreed) throws Exception {
        Map<String, Object> body = buyerBody("buyer01", "구매자1");
        body.put("privacyAgreed", privacyAgreed);
        body.put("thirdPartyAgreed", thirdPartyAgreed);

        mockMvc.perform(jsonPost("/api/auth/signup/buyer", body))
                .andExpect(status().isBadRequest());

        assertThat(countRows("users")).isZero();
        assertThat(countRows("address")).isZero();
    }

    @Test
    @DisplayName("A-09 이미 있는 아이디로 가입하면 409이고 행 수가 그대로다")
    void signupWithDuplicateLoginId() throws Exception {
        signupBuyer("buyer01", "구매자1");

        mockMvc.perform(jsonPost("/api/auth/signup/seller", sellerBody("buyer01", "다른닉네임")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));

        assertThat(countRows("users")).isEqualTo(1);
        assertThat(countRows("address")).isEqualTo(1);
        assertThat(countRows("seller_profile")).isZero();
    }

    @Test
    @DisplayName("A-11 이미 있는 닉네임으로 가입하면 409")
    void signupWithDuplicateNickname() throws Exception {
        signupBuyer("buyer01", "구매자1");

        mockMvc.perform(jsonPost("/api/auth/signup/buyer", buyerBody("buyer02", "구매자1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));

        assertThat(countRows("users")).isEqualTo(1);
    }

    @Test
    @DisplayName("A-12 필수값 누락과 전화번호, 우편번호 형식 오류는 400과 틀린 필드 이름을 준다")
    void signupWithInvalidFields() throws Exception {
        Map<String, Object> body = buyerBody("buyer01", "구매자1");
        body.remove("nickname");
        body.put("phone", "010-1234-5678");
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) body.get("address");
        address.put("zipCode", "1234");

        mockMvc.perform(jsonPost("/api/auth/signup/buyer", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.nickname").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors['address.zipCode']").exists());

        assertThat(countRows("users")).isZero();
    }

    @Test
    @DisplayName("A-15 우편번호 06236은 앞자리 0이 그대로 저장되고 조회된다")
    void zipCodeKeepsLeadingZero() throws Exception {
        MockHttpSession session = signupBuyer("buyer01", "구매자1");

        assertThat(jdbcTemplate.queryForObject("select zip_code from address", String.class)).isEqualTo("06236");
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress.zipCode").value("06236"));
    }

    @Test
    @DisplayName("A-16 비밀번호는 BCrypt 해시로만 저장되고 응답과 로그에 평문이 없다")
    void passwordIsHashed(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/auth/signup/buyer", buyerBody("buyer01", "구매자1")))
                .andExpect(status().isCreated())
                .andReturn();

        String hash = jdbcTemplate.queryForObject("select password_hash from users", String.class);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD);
        // MockMvc가 테스트 실패 분석용으로 찍는 요청 본문 덤프("Body = ")는 앱 로그가 아니라서 뺀다
        assertThat(output.getAll().lines()
                .filter(line -> !line.trim().startsWith("Body = "))
                .filter(line -> line.contains(PASSWORD)))
                .isEmpty();
    }

    @Test
    @DisplayName("A-17 가입 응답의 세션으로 바로 내 정보를 볼 수 있다 (자동 로그인)")
    void signupLogsIn() throws Exception {
        MockHttpSession session = signupBuyer("buyer01", "구매자1");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("buyer01"));
    }

    @Test
    @DisplayName("L-09 CSRF 토큰 없이 가입하면 403이고 행이 안 생긴다")
    void signupWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/signup/buyer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(buyerBody("buyer01", "구매자1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(countRows("users")).isZero();
    }
}
