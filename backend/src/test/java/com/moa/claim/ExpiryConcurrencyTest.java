package com.moa.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

// 동시 실행은 CountDownLatch로 같이 출발하고 10번 반복한다 (06 테스트 계획 0장).
// 만료는 스케줄러의 예외 삼키기를 거치지 않고 expire를 바로 불러서, 데드락이 나면 그대로 실패한다
class ExpiryConcurrencyTest extends ExpiryTestSupport {

    private static final int REPEAT = 10;

    @Test
    @DisplayName("E-03 같은 claim에 만료 2건 동시: EXPIRED 1번, quota -1 한 번, 새 claim 1개 (10번 반복)")
    void expireSameClaimTwiceAtTheSameTime() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");

        for (int round = 0; round < REPEAT; round++) {
            testClock.setTo(FIXED_NOW);
            Long postId = createOpenPost(seller, List.of(product("1", "니트")));
            Long productId = productIds(postId).get(0);
            Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
            Long nextCommentId = comment(next, postId, "1 니트").get("commentId").asLong();
            testClock.setTo(DEADLINE);

            Callable<Boolean> expire = () -> claimExpiryService.expire(claimId);
            List<Boolean> results = runAtTheSameTime(List.of(expire, expire));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
            assertThat(claimCount(productId)).isEqualTo(2);
            assertHandedOver(productId, claimId, nextCommentId, DEADLINE);
            assertThat(quotaOf(postId, "buyer01")).isZero();
            assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("E-05 신고(10:04:59.999)와 만료(10:05:00.001) 동시: 정확히 하나만 이김 (10번 반복)")
    void reportAndExpireAtTheSameTime() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        LocalDateTime reportAt = DEADLINE.minusNanos(1_000_000);
        LocalDateTime expireAt = DEADLINE.plusNanos(1_000_000);

        for (int round = 0; round < REPEAT; round++) {
            testClock.setTo(FIXED_NOW);
            Long postId = createOpenPost(seller, List.of(product("1", "니트")));
            Long productId = productIds(postId).get(0);
            Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
            Long nextCommentId = comment(next, postId, "1 니트").get("commentId").asLong();
            MockHttpServletRequestBuilder report = claimAction(buyer, claimId, "report");

            // 시계 하나를 스레드마다 다른 시각으로 본다. 두 요청이 같은 마감을 사이에 두고 겨룬다
            Callable<Object> reportTask = () -> {
                testClock.setForCurrentThread(reportAt);
                try {
                    return mockMvc.perform(report).andReturn().getResponse();
                } finally {
                    testClock.clearCurrentThread();
                }
            };
            Callable<Object> expireTask = () -> {
                testClock.setForCurrentThread(expireAt);
                try {
                    return claimExpiryService.expire(claimId);
                } finally {
                    testClock.clearCurrentThread();
                }
            };
            List<Object> results = runAtTheSameTime(List.of(reportTask, expireTask));
            MockHttpServletResponse reportResponse = (MockHttpServletResponse) results.get(0);
            boolean expired = (Boolean) results.get(1);

            if (claimStatus(claimId).equals("REPORTED")) {
                assertThat(reportResponse.getStatus()).isEqualTo(200);
                assertThat(expired).isFalse();
                assertThat(orderCount(claimId)).isEqualTo(1);
                assertThat(claimCount(productId)).isEqualTo(1);
                assertThat(currentClaimId(productId)).isEqualTo(claimId);
                assertThat(quotaOf(postId, "buyer01")).isEqualTo(1);
            } else {
                assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
                assertThat(reportResponse.getStatus()).isEqualTo(409);
                assertThat(jsonMapper.readTree(reportResponse.getContentAsString()).get("code").asString())
                        .isEqualTo("DEPOSIT_DEADLINE_PASSED");
                assertThat(expired).isTrue();
                assertThat(orderCount(claimId)).isZero();
                assertThat(claimCount(productId)).isEqualTo(2);
                assertHandedOver(productId, claimId, nextCommentId, expireAt);
                assertThat(quotaOf(postId, "buyer01")).isZero();
            }
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("S-05 후보 없는 상품의 만료와 같은 상품 정확한 새 댓글 2건 동시: 진행 중 claim 정확히 1개 (10번 반복)")
    void expireWhileNewOrdersArrive() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession second = signupBuyer("buyer02", "구매자2");
        MockHttpSession third = signupBuyer("buyer03", "구매자3");

        for (int round = 0; round < REPEAT; round++) {
            testClock.setTo(FIXED_NOW);
            Long postId = createOpenPost(seller, List.of(product("1", "니트")));
            Long productId = productIds(postId).get(0);
            Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
            testClock.setTo(DEADLINE);
            MockHttpServletRequestBuilder secondOrder = commentRequest(second, postId, "1 니트");
            MockHttpServletRequestBuilder thirdOrder = commentRequest(third, postId, "1 니트");

            List<Object> results = runAtTheSameTime(List.<Callable<Object>>of(
                    () -> claimExpiryService.expire(claimId),
                    () -> mockMvc.perform(secondOrder).andReturn().getResponse().getStatus(),
                    () -> mockMvc.perform(thirdOrder).andReturn().getResponse().getStatus()));

            assertThat(results).containsExactly(true, 201, 201);
            assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
            // 새 댓글이 후보로 잡혀 승계되든, ON_SALE을 보고 바로 당첨되든 한 명만
            assertThat(claimCount(productId)).isEqualTo(2);
            assertThat(productStatus(productId)).isEqualTo("RESERVED");
            assertThat(quotaOf(postId, "buyer02") + quotaOf(postId, "buyer03")).isEqualTo(1);
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("S-06 P1 만료(끝난 사람 B, 차순위 A)와 P2 입금 안 됨(끝난 사람 A, 차순위 B) 동시: 데드락 없이 둘 다 승계 (10번 반복)")
    void crossedSuccessionsDoNotDeadlock() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyerA = signupBuyer("buyer01", "구매자A");
        MockHttpSession buyerB = signupBuyer("buyer02", "구매자B");

        for (int round = 0; round < REPEAT; round++) {
            testClock.setTo(FIXED_NOW);
            Long postId = createOpenPost(seller, List.of(product("1", "니트"), product("2", "바지")));
            List<Long> products = productIds(postId);
            Long expiringClaimId = claimIdOf(comment(buyerB, postId, "1 니트"));
            Long rejectedClaimId = claimIdOf(comment(buyerA, postId, "2 바지"));
            Long commentOfA = comment(buyerA, postId, "1 니트").get("commentId").asLong();
            Long commentOfB = comment(buyerB, postId, "2 바지").get("commentId").asLong();
            assertThat(mockMvc.perform(claimAction(buyerA, rejectedClaimId, "report")).andReturn().getResponse()
                    .getStatus()).isEqualTo(200);
            testClock.setTo(DEADLINE);
            MockHttpServletRequestBuilder reject = claimAction(seller, rejectedClaimId, "reject");

            List<Object> results = runAtTheSameTime(List.<Callable<Object>>of(
                    () -> claimExpiryService.expire(expiringClaimId),
                    () -> mockMvc.perform(reject).andReturn().getResponse()));

            assertThat(results.get(0)).isEqualTo(true);
            MockHttpServletResponse rejectResponse = (MockHttpServletResponse) results.get(1);
            assertThat(rejectResponse.getStatus()).as(rejectResponse.getContentAsString()).isEqualTo(200);
            JsonNode nextClaim = jsonMapper.readTree(rejectResponse.getContentAsString()).get("nextClaim");
            assertThat(nextClaim.get("commentId").asLong()).isEqualTo(commentOfB);
            assertHandedOver(products.get(0), expiringClaimId, commentOfA, DEADLINE);
            assertHandedOver(products.get(1), rejectedClaimId, commentOfB, DEADLINE);
            assertThat(quotaOf(postId, "buyer01")).isEqualTo(1);
            assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
            assertClaimInvariants();
        }
    }
}
