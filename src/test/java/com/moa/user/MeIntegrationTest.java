package com.moa.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class MeIntegrationTest extends IntegrationTestSupport {

    private static final Map<String, Object> NEW_ACCOUNT = Map.of(
            "bankName", "신한은행", "accountNumber", "110-222-333333", "accountHolder", "김판매");

    @Test
    @DisplayName("M-01 구매자 내 정보: 기본 배송지는 있고 sellerProfile과 비밀번호는 없다")
    void buyerMe() throws Exception {
        MockHttpSession session = signupBuyer("buyer01", "구매자1");

        String body = mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("buyer01"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.nickname").value("구매자1"))
                .andExpect(jsonPath("$.realName").value("홍길동"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.defaultAddress.address1").value("서울시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.sellerProfile").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("password");
    }

    @Test
    @DisplayName("M-02 판매자 내 정보에는 계좌 정보도 있다")
    void sellerMe() throws Exception {
        MockHttpSession session = signupSeller("seller01", "판매자1");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.defaultAddress.zipCode").value("06236"))
                .andExpect(jsonPath("$.sellerProfile.bankName").value("국민은행"))
                .andExpect(jsonPath("$.sellerProfile.accountNumber").value("123-456-7890"))
                .andExpect(jsonPath("$.sellerProfile.accountHolder").value("홍길동"));
    }

    @Test
    @DisplayName("M-03 비로그인은 302 리다이렉트가 아니라 401 JSON")
    void anonymousGets401Json() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(sellerProfileRequest(NEW_ACCOUNT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("M-04 판매자가 계좌를 고치면 본인 seller_profile만 바뀐다")
    void sellerChangesAccount() throws Exception {
        MockHttpSession session = signupSeller("seller01", "판매자1");
        signupSeller("seller02", "판매자2");

        mockMvc.perform(sellerProfileRequest(NEW_ACCOUNT).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("신한은행"))
                .andExpect(jsonPath("$.accountNumber").value("110-222-333333"))
                .andExpect(jsonPath("$.accountHolder").value("김판매"));

        String sql = "select sp.bank_name from seller_profile sp join users u on u.id = sp.user_id where u.login_id = ?";
        assertThat(jdbcTemplate.queryForObject(sql, String.class, "seller01")).isEqualTo("신한은행");
        assertThat(jdbcTemplate.queryForObject(sql, String.class, "seller02")).isEqualTo("국민은행");
    }

    @Test
    @DisplayName("M-05 구매자가 계좌 수정 API를 부르면 403이고 seller_profile이 안 생긴다")
    void buyerCannotChangeAccount() throws Exception {
        MockHttpSession session = signupBuyer("buyer01", "구매자1");

        mockMvc.perform(sellerProfileRequest(NEW_ACCOUNT).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(countRows("seller_profile")).isZero();
    }

    private MockHttpServletRequestBuilder sellerProfileRequest(Map<String, Object> body) throws Exception {
        return withCsrf(put("/api/me/seller-profile"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }
}
