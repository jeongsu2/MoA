package com.moa.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

class AddressIntegrationTest extends IntegrationTestSupport {

    private static final String ADDRESSES = "/api/me/addresses";
    private static final long MISSING_ID = 999_999L;

    @AfterEach
    void checkOneDefaultPerUser() {
        assertThat(countUsersWithoutExactlyOneDefault()).isZero();
    }

    @Test
    @DisplayName("R-01 목록에는 내 배송지만 있고 기본이 맨 위, 기본은 1개")
    void listMyAddresses() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        addAddress(me, "11111", false);
        addAddress(me, "22222", false);
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        addAddress(other, "33333", false);

        String response = mockMvc.perform(get(ADDRESSES).session(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].zipCode").value("06236"))
                .andReturn().getResponse().getContentAsString();

        List<Boolean> defaults = new ArrayList<>();
        List<String> zipCodes = new ArrayList<>();
        for (JsonNode address : jsonMapper.readTree(response)) {
            defaults.add(address.get("isDefault").asBoolean());
            zipCodes.add(address.get("zipCode").asString());
        }
        assertThat(defaults).containsExactly(true, false, false);
        assertThat(zipCodes).containsExactly("06236", "11111", "22222");
    }

    @Test
    @DisplayName("C-01, C-02 추가: 그냥 추가하면 기본 그대로, setDefault=true면 새 것만 기본")
    void addAddress() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        Long firstId = defaultAddressId("buyer01");

        mockMvc.perform(addRequest(me, "11111", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zipCode").value("11111"))
                .andExpect(jsonPath("$.isDefault").value(false));
        assertThat(defaultAddressId("buyer01")).isEqualTo(firstId);

        Long newId = addAddress(me, "06236", true);

        assertThat(defaultAddressId("buyer01")).isEqualTo(newId);
        assertThat(isDefault(firstId)).isFalse();
        assertThat(countAddresses("buyer01")).isEqualTo(3);
    }

    @Test
    @DisplayName("U-01 수정하면 그 행만 바뀌고 기본 여부는 그대로")
    void changeAddress() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        Long defaultId = defaultAddressId("buyer01");
        Long otherId = addAddress(me, "11111", false);

        Map<String, Object> body = addressBody("63100");
        body.put("address1", "제주시 첨단로 1");
        mockMvc.perform(withCsrf(put(ADDRESSES + "/" + defaultId)).session(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("63100"))
                .andExpect(jsonPath("$.address1").value("제주시 첨단로 1"))
                .andExpect(jsonPath("$.isDefault").value(true));

        assertThat(zipCodeOf(defaultId)).isEqualTo("63100");
        assertThat(zipCodeOf(otherId)).isEqualTo("11111");
    }

    @Test
    @DisplayName("X-01 기본이 아닌 배송지는 지워진다")
    void deleteAddress() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        Long otherId = addAddress(me, "11111", false);

        mockMvc.perform(withCsrf(delete(ADDRESSES + "/" + otherId)).session(me))
                .andExpect(status().isNoContent());

        assertThat(countAddresses("buyer01")).isEqualTo(1);
    }

    @Test
    @DisplayName("X-03, X-04 기본 배송지는 삭제 거부, 행 그대로")
    void cannotDeleteDefaultAddress() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        Long defaultId = defaultAddressId("buyer01");

        mockMvc.perform(withCsrf(delete(ADDRESSES + "/" + defaultId)).session(me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEFAULT_ADDRESS_NOT_DELETABLE"));

        addAddress(me, "11111", false);
        mockMvc.perform(withCsrf(delete(ADDRESSES + "/" + defaultId)).session(me))
                .andExpect(status().isConflict());

        assertThat(countAddresses("buyer01")).isEqualTo(2);
        assertThat(defaultAddressId("buyer01")).isEqualTo(defaultId);
    }

    @Test
    @DisplayName("S-01, S-02 기본 변경: 대상만 기본, 같은 요청을 다시 보내도 결과 같음")
    void changeDefaultAddress() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        Long firstId = defaultAddressId("buyer01");
        Long secondId = addAddress(me, "11111", false);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(withCsrf(put(ADDRESSES + "/" + secondId + "/default")).session(me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(secondId))
                    .andExpect(jsonPath("$[0].isDefault").value(true))
                    .andExpect(jsonPath("$[1].isDefault").value(false));
        }

        assertThat(isDefault(secondId)).isTrue();
        assertThat(isDefault(firstId)).isFalse();
    }

    @Test
    @DisplayName("C-05 10개까지 추가되고 11번째는 400 (가입 때 만든 1개 포함)")
    void addressLimit() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        for (int i = 0; i < AddressService.MAX_ADDRESS_COUNT - 1; i++) {
            addAddress(me, "1000" + i, false);
        }

        mockMvc.perform(addRequest(me, "20000", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));

        assertThat(countAddresses("buyer01")).isEqualTo(AddressService.MAX_ADDRESS_COUNT);
        assertThat(countRowsWithZipCode("20000")).isZero();
    }

    @Test
    @DisplayName("I-01~I-04 남의 배송지 id로 수정, 삭제, 기본 변경은 404, 없는 id와 응답 같고 양쪽 행 그대로")
    void cannotTouchOthersAddress() throws Exception {
        MockHttpSession me = signupBuyer("buyer01", "구매자1");
        addAddress(me, "11111", false);
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        addAddress(other, "22222", false);
        Long othersDefaultId = defaultAddressId("buyer02");
        Long othersOtherId = jdbcTemplate.queryForObject(
                "select id from address where zip_code = '22222'", Long.class);
        List<Map<String, Object>> before = allAddressRows();

        for (Long othersId : List.of(othersDefaultId, othersOtherId)) {
            String changeBody = jsonMapper.writeValueAsString(addressBody("99999"));
            String changeOthers = notFoundBody(withCsrf(put(ADDRESSES + "/" + othersId)).session(me)
                    .contentType(MediaType.APPLICATION_JSON).content(changeBody));
            String changeMissing = notFoundBody(withCsrf(put(ADDRESSES + "/" + MISSING_ID)).session(me)
                    .contentType(MediaType.APPLICATION_JSON).content(changeBody));
            assertThat(changeOthers).isEqualTo(changeMissing);

            String deleteOthers = notFoundBody(withCsrf(delete(ADDRESSES + "/" + othersId)).session(me));
            String deleteMissing = notFoundBody(withCsrf(delete(ADDRESSES + "/" + MISSING_ID)).session(me));
            assertThat(deleteOthers).isEqualTo(deleteMissing);

            String defaultOthers = notFoundBody(
                    withCsrf(put(ADDRESSES + "/" + othersId + "/default")).session(me));
            String defaultMissing = notFoundBody(
                    withCsrf(put(ADDRESSES + "/" + MISSING_ID + "/default")).session(me));
            assertThat(defaultOthers).isEqualTo(defaultMissing);
        }

        assertThat(allAddressRows()).isEqualTo(before);
    }

    private String notFoundBody(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder addRequest(MockHttpSession session, String zipCode, boolean setDefault)
            throws Exception {
        Map<String, Object> body = addressBody(zipCode);
        body.put("setDefault", setDefault);
        return withCsrf(post(ADDRESSES)).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    private Long addAddress(MockHttpSession session, String zipCode, boolean setDefault) throws Exception {
        String response = mockMvc.perform(addRequest(session, zipCode, setDefault))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    private Long defaultAddressId(String loginId) {
        return jdbcTemplate.queryForObject(
                "select a.id from address a join users u on u.id = a.user_id"
                        + " where u.login_id = ? and a.is_default = true",
                Long.class, loginId);
    }

    private int countAddresses(String loginId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from address a join users u on u.id = a.user_id where u.login_id = ?",
                Integer.class, loginId);
        return count == null ? 0 : count;
    }

    private int countRowsWithZipCode(String zipCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from address where zip_code = ?", Integer.class, zipCode);
        return count == null ? 0 : count;
    }

    private boolean isDefault(Long addressId) {
        Boolean value = jdbcTemplate.queryForObject(
                "select is_default from address where id = ?", Boolean.class, addressId);
        return Boolean.TRUE.equals(value);
    }

    private String zipCodeOf(Long addressId) {
        return jdbcTemplate.queryForObject("select zip_code from address where id = ?", String.class, addressId);
    }

    private List<Map<String, Object>> allAddressRows() {
        return jdbcTemplate.queryForList("select * from address order by id");
    }
}
