package com.moa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class LoginIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("L-03 없는 아이디와 틀린 비밀번호는 둘 다 401이고 응답 본문이 같다")
    void loginFailureHidesReason() throws Exception {
        signupBuyer("buyer01", "구매자1");

        String unknownId = mockMvc.perform(jsonPost("/api/auth/login", Map.of("loginId", "nobody", "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = mockMvc.perform(jsonPost("/api/auth/login", Map.of("loginId", "buyer01", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownId).isEqualTo(wrongPassword).contains("LOGIN_FAILED");
    }

    @Test
    @DisplayName("L-05 정지된 계정은 비밀번호가 맞아도 로그인 403")
    void suspendedUserCannotLogin() throws Exception {
        signupBuyer("buyer01", "구매자1");
        jdbcTemplate.update("update users set status = 'SUSPENDED' where login_id = 'buyer01'");

        mockMvc.perform(jsonPost("/api/auth/login", Map.of("loginId", "buyer01", "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("L-06 로그인된 상태에서 정지되면 다음 요청부터 403")
    void suspendedWhileLoggedIn() throws Exception {
        MockHttpSession session = signupBuyer("buyer01", "구매자1");
        mockMvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

        jdbcTemplate.update("update users set status = 'SUSPENDED' where login_id = 'buyer01'");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }
}
