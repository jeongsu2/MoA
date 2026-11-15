package com.moa.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

// 동시 요청은 CountDownLatch로 같이 출발하고 10번 반복한다. 한 번이라도 깨지면 버그 (04 테스트 계획 0장).
// 응답이 모두 201이어야 하므로 500이나 데드락 예외가 하나라도 있으면 실패한다
class CommentConcurrencyTest extends ClaimTestSupport {

    private static final int REPEAT = 10;
    private static final int THREADS = 20;

    @Test
    @DisplayName("W-03 같은 상품에 서로 다른 20명이 정확한 댓글 동시: 당첨 1명, ORDER 20개 (10번 반복)")
    void onlyOneWinnerForSameProduct() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        List<MockHttpSession> buyers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            buyers.add(signupBuyer("buyer" + (100 + i), "구매자" + i));
        }

        for (int round = 0; round < REPEAT; round++) {
            Long postId = createOpenPost(seller, List.of(product("1", "니트")));
            Long productId = productIds(postId).get(0);
            List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
            for (MockHttpSession buyer : buyers) {
                requests.add(commentRequest(buyer, postId, "1 니트"));
            }

            List<JsonNode> results = sendAtTheSameTime(requests);

            List<JsonNode> winners = results.stream().filter(result -> result.get("won").asBoolean()).toList();
            assertThat(winners).hasSize(1);
            assertThat(results).allMatch(result -> "ORDER".equals(result.get("type").asString()));
            assertThat(claimCount(productId)).isEqualTo(1);
            Long claimId = winners.get(0).get("claim").get("claimId").asLong();
            assertThat(currentClaimId(productId)).isEqualTo(claimId);
            assertThat(productStatus(productId)).isEqualTo("RESERVED");
            // 당첨 댓글 id가 가장 작지 않아도 된다 (D-22). 당첨 claim이 응답의 댓글을 가리키는지만 본다
            assertThat(jdbcTemplate.queryForObject("select comment_id from claim where id = ?", Long.class, claimId))
                    .isEqualTo(winners.get(0).get("commentId").asLong());
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from comment where post_id = ? and type = 'ORDER' and product_id = ?",
                    Integer.class, postId, productId)).isEqualTo(THREADS);
            // 진 19명은 quota 행이 없다. ON_SALE을 본 사람만 행을 만든다
            assertThat(jdbcTemplate.queryForObject(
                    "select coalesce(sum(active_count), 0) from purchase_quota where post_id = ?",
                    Integer.class, postId)).isEqualTo(1);
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("Q-02 4개 당첨 중인 사람이 5번째와 6번째에 동시 댓글: 당첨 1개, quota 5, 진 상품 ON_SALE (10번 반복)")
    void fifthAndSixthAtTheSameTime() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");

        for (int round = 0; round < REPEAT; round++) {
            Long postId = createOpenPost(seller, numberedProducts(6));
            for (int i = 1; i <= 4; i++) {
                assertThat(comment(buyer, postId, i + " 상품" + i).get("won").asBoolean()).isTrue();
            }

            List<JsonNode> results = sendAtTheSameTime(List.of(
                    commentRequest(buyer, postId, "5 상품5"),
                    commentRequest(buyer, postId, "6 상품6")));

            assertThat(results.stream().filter(result -> result.get("won").asBoolean())).hasSize(1);
            assertThat(quotaOf(postId, "buyer01")).isEqualTo(5);
            JsonNode loser = results.stream().filter(result -> !result.get("won").asBoolean()).findFirst()
                    .orElseThrow();
            Long loserProductId = loser.get("productId").asLong();
            assertThat(loser.get("type").asString()).isEqualTo("ORDER");
            // quota를 못 얻었는데 상품만 RESERVED가 되면 버그
            assertThat(productStatus(loserProductId)).isEqualTo("ON_SALE");
            assertThat(currentClaimId(loserProductId)).isNull();
            assertThat(claimCount(loserProductId)).isZero();
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("Q-03 quota 행이 없는 사람이 상품 6개에 동시 댓글: 당첨 5개, 중복 키 500 없음 (10번 반복)")
    void sixProductsAtTheSameTimeWithoutQuotaRow() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");

        for (int round = 0; round < REPEAT; round++) {
            Long postId = createOpenPost(seller, numberedProducts(6));
            List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                requests.add(commentRequest(buyer, postId, i + " 상품" + i));
            }

            List<JsonNode> results = sendAtTheSameTime(requests);

            assertThat(results.stream().filter(result -> result.get("won").asBoolean())).hasSize(5);
            assertThat(jdbcTemplate.queryForObject("select count(*) from purchase_quota where post_id = ?",
                    Integer.class, postId)).isEqualTo(1);
            assertThat(quotaOf(postId, "buyer01")).isEqualTo(5);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from product where post_id = ? and status = 'ON_SALE' and current_claim_id is null",
                    Integer.class, postId)).isEqualTo(1);
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("Q-04 두 사람이 상품 두 개에 엇갈려 동시 댓글: 상품마다 claim 1개, 데드락 없음 (10번 반복)")
    void crossedOrdersHaveNoDeadlock() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession first = signupBuyer("buyer01", "구매자1");
        MockHttpSession second = signupBuyer("buyer02", "구매자2");

        for (int round = 0; round < REPEAT; round++) {
            Long postId = createOpenPost(seller, numberedProducts(2));
            List<Long> productIds = productIds(postId);

            List<JsonNode> results = sendAtTheSameTime(List.of(
                    commentRequest(first, postId, "1 상품1"),
                    commentRequest(first, postId, "2 상품2"),
                    commentRequest(second, postId, "2 상품2"),
                    commentRequest(second, postId, "1 상품1")));

            assertThat(results.stream().filter(result -> result.get("won").asBoolean())).hasSize(2);
            for (Long productId : productIds) {
                assertThat(claimCount(productId)).isEqualTo(1);
                assertThat(productStatus(productId)).isEqualTo("RESERVED");
            }
            assertClaimInvariants();
        }
    }

    // CSRF 토큰은 미리 받아 두고, 모든 스레드가 준비되면 같이 출발한다. 응답은 모두 201이어야 한다
    private List<JsonNode> sendAtTheSameTime(List<MockHttpServletRequestBuilder> requests)
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(request).andReturn().getResponse();
                }));
            }
            ready.await();
            start.countDown();

            List<JsonNode> results = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                MockHttpServletResponse response = future.get(60, TimeUnit.SECONDS);
                assertThat(response.getStatus()).as(responseBody(response)).isEqualTo(201);
                results.add(jsonMapper.readTree(responseBody(response)));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private String responseBody(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
