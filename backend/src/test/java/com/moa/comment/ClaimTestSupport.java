package com.moa.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

// 댓글 주문, 입금 테스트 공통. 모든 테스트 끝에 당첨 불변식을 SQL로 직접 확인한다 (04, 05 테스트 계획 0장)
public abstract class ClaimTestSupport extends IntegrationTestSupport {

    protected static final String POSTS = "/api/posts";

    @AfterEach
    protected void checkClaimInvariants() {
        assertClaimInvariants();
    }

    protected void assertClaimInvariants() {
        assertThat(countProductsWithManyActiveClaims()).as("I-1 상품마다 진행 중 claim 1개 이하").isZero();
        assertThat(countProductsWithBrokenCurrentClaim()).as("I-2 current_claim_id와 상태 일치").isZero();
        assertThat(countBrokenQuotas()).as("I-3 quota = 진행 중 + 확정 claim 수").isZero();
        assertThat(countBrokenOrders()).as("05 I-1 주문은 claim마다 1개 이하, 신고, 확정은 정확히 1개").isZero();
        assertThat(countBrokenThanksComments()).as("05 I-2 감사 댓글은 확정 claim에만 정확히 1개").isZero();
        assertThat(countBrokenDepositReports()).as("05 I-3 DEPOSIT_REPORT 댓글은 claim마다 1개 이하").isZero();
    }

    // 05 I-1. 신고 전(WON)과 만료는 0개, 신고를 거친 상태는 1개. 입금 안 됨도 주문 행이 기록으로 남아 1개
    private int countBrokenOrders() {
        return queryCount("select count(*) from claim c where (select count(*) from orders o where o.claim_id = c.id)"
                + " <> case when c.status in ('REPORTED', 'CONFIRMED', 'REJECTED') then 1 else 0 end");
    }

    // 05 I-2. 감사 댓글은 당첨 댓글에 단 SYSTEM 대댓글
    private int countBrokenThanksComments() {
        return queryCount("select count(*) from claim c where (select count(*) from comment m"
                + " where m.parent_id = c.comment_id and m.type = 'SYSTEM')"
                + " <> case when c.status = 'CONFIRMED' then 1 else 0 end");
    }

    // 05 I-3
    private int countBrokenDepositReports() {
        return queryCount("select count(*) from claim c where (select count(*) from comment m"
                + " where m.parent_id = c.comment_id and m.type = 'DEPOSIT_REPORT') > 1");
    }

    private int queryCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    protected Map<String, Object> product(String itemNo, String name) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("itemNo", itemNo);
        product.put("name", name);
        product.put("price", 15000);
        return product;
    }

    protected Long createDraftPost(MockHttpSession seller, List<Map<String, Object>> products) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "봄 옷 정리");
        body.put("content", "번호와 이름을 댓글로 남겨주세요");
        body.put("imageKeys", List.of());
        body.put("products", products);
        String response = mockMvc.perform(jsonPost(POSTS, body).session(seller))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("postId").asLong();
    }

    protected Long createOpenPost(MockHttpSession seller, List<Map<String, Object>> products) throws Exception {
        Long postId = createDraftPost(seller, products);
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/open")).session(seller))
                .andExpect(status().isOk());
        return postId;
    }

    // 상품 1번부터 count번까지, 이름은 "상품N"
    protected List<Map<String, Object>> numberedProducts(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> product(String.valueOf(i), "상품" + i))
                .toList();
    }

    protected List<Long> productIds(Long postId) {
        return jdbcTemplate.queryForList("select id from product where post_id = ? order by id", Long.class, postId);
    }

    protected MockHttpServletRequestBuilder commentRequest(MockHttpSession session, Long postId, String content)
            throws Exception {
        MockHttpServletRequestBuilder request = jsonPost(POSTS + "/" + postId + "/comments",
                Map.of("content", content));
        return session == null ? request : request.session(session);
    }

    protected JsonNode comment(MockHttpSession session, Long postId, String content) throws Exception {
        String response = mockMvc.perform(commentRequest(session, postId, content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }

    protected String productStatus(Long productId) {
        return jdbcTemplate.queryForObject("select status from product where id = ?", String.class, productId);
    }

    protected Long currentClaimId(Long productId) {
        return jdbcTemplate.queryForObject("select current_claim_id from product where id = ?", Long.class,
                productId);
    }

    protected int quotaOf(Long postId, String loginId) {
        List<Integer> counts = jdbcTemplate.queryForList("select q.active_count from purchase_quota q"
                + " join users u on u.id = q.user_id where q.post_id = ? and u.login_id = ?",
                Integer.class, postId, loginId);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    protected int claimCount(Long productId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from claim where product_id = ?",
                Integer.class, productId);
        return count == null ? 0 : count;
    }
}
