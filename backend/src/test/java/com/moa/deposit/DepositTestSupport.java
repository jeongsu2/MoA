package com.moa.deposit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.comment.ClaimTestSupport;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

// 입금 테스트 공통. 당첨은 늘 FIXED_NOW(10:00:00)에 일어나서 deadline_at은 10:05:00 (05 테스트 계획 0장)
abstract class DepositTestSupport extends ClaimTestSupport {

    protected static final String CLAIMS = "/api/claims";
    protected static final String ADDRESSES = "/api/me/addresses";
    protected static final int PRICE = 15000;

    protected record Won(Long postId, Long productId, Long claimId, Long commentId) {
    }

    // 상품 1개짜리 글을 열고 buyer가 정확한 댓글로 당첨된다
    protected Won win(MockHttpSession seller, MockHttpSession buyer) throws Exception {
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        JsonNode comment = comment(buyer, postId, "1 니트");
        return new Won(postId, productIds(postId).get(0), comment.get("claim").get("claimId").asLong(),
                comment.get("commentId").asLong());
    }

    protected MockHttpServletRequestBuilder claimAction(MockHttpSession session, Long claimId, String action)
            throws Exception {
        MockHttpServletRequestBuilder request = withCsrf(post(CLAIMS + "/" + claimId + "/" + action));
        return session == null ? request : request.session(session);
    }

    protected JsonNode reportOk(MockHttpSession buyer, Long claimId) throws Exception {
        String response = mockMvc.perform(claimAction(buyer, claimId, "report"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }

    protected Long addAddress(MockHttpSession session, Map<String, Object> address) throws Exception {
        String response = mockMvc.perform(jsonPost(ADDRESSES, address).session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    protected MockHttpServletRequestBuilder changeDefaultRequest(MockHttpSession session, Long addressId)
            throws Exception {
        return withCsrf(put(ADDRESSES + "/" + addressId + "/default")).session(session);
    }

    protected MockHttpServletRequestBuilder changeAddressRequest(MockHttpSession session, Long addressId,
            Map<String, Object> address) throws Exception {
        return withCsrf(put(ADDRESSES + "/" + addressId)).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(address));
    }

    // 필드마다 다른 값이라 주문 주소가 두 배송지에서 섞여 들어오면 바로 보인다
    protected Map<String, Object> address(String tag, String zipCode) {
        Map<String, Object> address = addressBody(zipCode);
        address.put("recipientName", "받는" + tag);
        address.put("recipientPhone", tag.equals("B") ? "01022222222" : "01033333333");
        address.put("address1", tag + "시 " + tag + "구 1");
        address.put("address2", tag + "동 101호");
        address.put("deliveryNote", tag + " 문 앞");
        return address;
    }

    protected Map<String, Object> orderRow(Long claimId) {
        return jdbcTemplate.queryForMap("select * from orders where claim_id = ?", claimId);
    }

    protected int orderCount(Long claimId) {
        return count("select count(*) from orders where claim_id = ?", claimId);
    }

    protected int replyCount(Long winningCommentId, String type) {
        return count("select count(*) from comment where parent_id = ? and type = ?", winningCommentId, type);
    }

    protected String claimStatus(Long claimId) {
        return jdbcTemplate.queryForObject("select status from claim where id = ?", String.class, claimId);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
