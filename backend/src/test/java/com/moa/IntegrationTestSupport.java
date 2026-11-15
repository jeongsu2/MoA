package com.moa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

// 통합 테스트 공통. 동시성 테스트가 있어서 @Transactional 롤백 대신 테스트마다 테이블을 비운다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "password123!";
    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";
    protected static final String CSRF_COOKIE = "XSRF-TOKEN";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    protected void cleanTables() {
        jdbcTemplate.update("delete from address");
        jdbcTemplate.update("delete from seller_profile");
        jdbcTemplate.update("delete from users");
    }

    // 불변식: 모든 사용자는 기본 배송지가 정확히 1개 (가입 때 1개가 생기므로 배송지 0개인 사용자는 없다)
    protected int countUsersWithoutExactlyOneDefault() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users u where (select count(*) from address a"
                        + " where a.user_id = u.id and a.is_default = true) <> 1",
                Integer.class);
        return count == null ? 0 : count;
    }

    protected int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    // GET /api/auth/csrf로 받은 토큰을 쿠키와 헤더에 같이 붙인다 (프론트와 같은 방식)
    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE);
        return builder.cookie(csrfCookie).header(CSRF_HEADER, csrfCookie.getValue());
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return withCsrf(post(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    protected Map<String, Object> addressBody(String zipCode) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("recipientName", "홍길동");
        address.put("recipientPhone", "01012345678");
        address.put("zipCode", zipCode);
        address.put("address1", "서울시 강남구 테헤란로 1");
        address.put("address2", "101호");
        address.put("deliveryNote", "공동현관 1234");
        return address;
    }

    protected Map<String, Object> buyerBody(String loginId, String nickname) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", loginId);
        body.put("password", PASSWORD);
        body.put("nickname", nickname);
        body.put("realName", "홍길동");
        body.put("phone", "01012345678");
        body.put("address", addressBody("06236"));
        body.put("privacyAgreed", true);
        body.put("thirdPartyAgreed", true);
        return body;
    }

    protected Map<String, Object> sellerBody(String loginId, String nickname) {
        Map<String, Object> body = buyerBody(loginId, nickname);
        body.put("bankName", "국민은행");
        body.put("accountNumber", "123-456-7890");
        body.put("accountHolder", "홍길동");
        return body;
    }

    // 가입하면 자동 로그인되므로 그 세션을 돌려준다
    protected MockHttpSession signupBuyer(String loginId, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/auth/signup/buyer", buyerBody(loginId, nickname)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession signupSeller(String loginId, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/auth/signup/seller", sellerBody(loginId, nickname)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
