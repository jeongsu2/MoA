package com.moa.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.JsonNode;

// 06 테스트 계획 1장, 2장의 순차 시나리오. 동시 실행은 ExpiryConcurrencyTest
class ExpiryIntegrationTest extends ExpiryTestSupport {

    @Test
    @DisplayName("E-01 10:04:59에는 아무것도 안 바뀌고 정확히 10:05:00에 만료. quota -1, 차순위가 새 5분으로 당첨")
    void expireAtExactlyFiveMinutes() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        Long nextCommentId = comment(next, postId, "1 니트").get("commentId").asLong();

        testClock.setTo(DEADLINE.minusSeconds(1));
        assertThat(claimExpiryScheduler.expireDueClaims()).isZero();
        assertThat(claimStatus(claimId)).isEqualTo("WON");
        assertThat(currentClaimId(productId)).isEqualTo(claimId);
        assertThat(quotaOf(postId, "buyer01")).isEqualTo(1);

        testClock.setTo(DEADLINE);
        assertThat(claimExpiryScheduler.expireDueClaims()).isEqualTo(1);

        assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
        assertThat(claimTime(claimId, "ended_at")).isEqualTo(DEADLINE);
        assertThat(quotaOf(postId, "buyer01")).isZero();
        assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
        assertHandedOver(productId, claimId, nextCommentId, DEADLINE);
        assertThat(claimCount(productId)).isEqualTo(2);
    }

    @Test
    @DisplayName("E-02 정확히 10:05:00에 신고는 409, 같은 시각 만료는 성공")
    void reportFailsAndExpireSucceedsAtDeadline() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        testClock.setTo(DEADLINE);

        mockMvc.perform(claimAction(buyer, claimId, "report"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOSIT_DEADLINE_PASSED"));
        assertThat(claimExpiryService.expire(claimId)).isTrue();

        assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
        assertThat(orderCount(claimId)).isZero();
        // 만료 뒤 신고도 같은 409
        mockMvc.perform(claimAction(buyer, claimId, "report"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOSIT_DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("E-04 만료를 연달아 두 번: 두 번째는 아무것도 안 바꿈, 새 claim 1개 그대로")
    void expireTwiceInARow() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        comment(next, postId, "1 니트");
        testClock.setTo(DEADLINE.plusSeconds(1));

        assertThat(claimExpiryService.expire(claimId)).isTrue();
        Long successor = currentClaimId(productId);
        assertThat(claimExpiryService.expire(claimId)).isFalse();
        assertThat(claimExpiryScheduler.expireDueClaims()).isZero();

        assertThat(currentClaimId(productId)).isEqualTo(successor);
        assertThat(claimCount(productId)).isEqualTo(2);
        assertThat(quotaOf(postId, "buyer01")).isZero();
        assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
    }

    @Test
    @DisplayName("E-06 마감 지난 claim 여러 개를 한 번 실행으로 모두 처리. 메모리 타이머 없이 DB만 본다")
    void expireManyDueClaimsInOneRun() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, numberedProducts(3));
        List<Long> claimIds = List.of(claimIdOf(comment(buyer, postId, "1 상품1")),
                claimIdOf(comment(buyer, postId, "2 상품2")), claimIdOf(comment(buyer, postId, "3 상품3")));
        testClock.setTo(FIXED_NOW.plusHours(3));

        assertThat(claimExpiryScheduler.expireDueClaims()).isEqualTo(3);

        for (Long claimId : claimIds) {
            assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
        }
        for (Long productId : productIds(postId)) {
            assertThat(productStatus(productId)).isEqualTo("ON_SALE");
        }
        assertThat(quotaOf(postId, "buyer01")).isZero();
    }

    @Test
    @DisplayName("S-01 Q-1 A: 차순위는 당첨된 적 없는 ORDER 댓글 중 가장 작은 id. 락 경쟁에서 진 앞 댓글도 기회를 받음")
    void successorIsSmallestUnclaimedComment() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession early = signupBuyer("buyer02", "구매자2");
        MockHttpSession late = signupBuyer("buyer03", "구매자3");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        // 락 경쟁에서 진 앞 댓글을 흉내 낸다. 당첨 댓글보다 id가 작고 claim이 없는 ORDER 댓글
        Long earlyCommentId = comment(early, postId, "안녕하세요").get("commentId").asLong();
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        jdbcTemplate.update("update comment set type = 'ORDER', product_id = ?, content = '1 니트' where id = ?",
                productId, earlyCommentId);
        comment(late, postId, "1 니트");
        testClock.setTo(DEADLINE);

        assertThat(claimExpiryService.expire(claimId)).isTrue();

        assertHandedOver(productId, claimId, earlyCommentId, DEADLINE);
        assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
        assertThat(quotaOf(postId, "buyer03")).isZero();
    }

    @Test
    @DisplayName("S-02 첫 후보 작성자가 그 글에서 이미 5개면 건너뛰고 다음 후보. 건너뛴 사람 quota 5 그대로")
    void skipCandidateWithFullQuota() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession full = signupBuyer("buyer02", "구매자2");
        MockHttpSession next = signupBuyer("buyer03", "구매자3");
        Long postId = createOpenPost(seller, numberedProducts(6));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 상품1"));
        for (int i = 2; i <= 6; i++) {
            assertThat(claimIdOf(comment(full, postId, i + " 상품" + i))).isNotNull();
        }
        comment(full, postId, "1 상품1");
        Long nextCommentId = comment(next, postId, "1 상품1").get("commentId").asLong();
        testClock.setTo(DEADLINE);

        assertThat(claimExpiryService.expire(claimId)).isTrue();

        assertHandedOver(productId, claimId, nextCommentId, DEADLINE);
        assertThat(quotaOf(postId, "buyer02")).isEqualTo(5);
        assertThat(quotaOf(postId, "buyer03")).isEqualTo(1);
    }

    @Test
    @DisplayName("S-03 후보가 없으면 ON_SALE, current_claim_id null. 그 뒤 첫 정확한 새 댓글이 새 5분으로 당첨")
    void noCandidateReturnsToSale() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        // 일반 댓글은 후보가 아니다
        comment(other, postId, "예뻐요");
        testClock.setTo(DEADLINE.plusSeconds(30));

        assertThat(claimExpiryService.expire(claimId)).isTrue();
        assertThat(productStatus(productId)).isEqualTo("ON_SALE");
        assertThat(currentClaimId(productId)).isNull();
        assertThat(claimCount(productId)).isEqualTo(1);

        LocalDateTime later = DEADLINE.plusMinutes(1);
        testClock.setTo(later);
        JsonNode retry = comment(other, postId, "1 니트");
        assertHandedOver(productId, claimId, retry.get("commentId").asLong(), later);
    }

    @Test
    @DisplayName("S-04 Q-2 A: 만료된 당첨 댓글은 다시 후보가 아니고, 같은 사람이 미리 써 둔 댓글은 id 순서대로 후보")
    void expiredBuyersEarlierCommentIsCandidate() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        JsonNode first = comment(buyer, postId, "1 니트");
        Long claimId = claimIdOf(first);
        Long secondCommentId = comment(buyer, postId, "1 니트").get("commentId").asLong();
        Long otherCommentId = comment(other, postId, "1 니트").get("commentId").asLong();
        testClock.setTo(DEADLINE);

        assertThat(claimExpiryService.expire(claimId)).isTrue();
        Long secondClaimId = assertHandedOver(productId, claimId, secondCommentId, DEADLINE);
        // 돌려받고 다시 얻어서 1 그대로
        assertThat(quotaOf(postId, "buyer01")).isEqualTo(1);

        // 두 번째도 만료되면 다음은 다른 사람. 처음 당첨 댓글은 다시 후보가 되지 않는다
        LocalDateTime secondDeadline = DEADLINE.plusMinutes(5);
        testClock.setTo(secondDeadline);
        assertThat(claimExpiryService.expire(secondClaimId)).isTrue();
        assertHandedOver(productId, secondClaimId, otherCommentId, secondDeadline);
        assertThat(jdbcTemplate.queryForObject("select count(*) from claim where comment_id = ?", Integer.class,
                first.get("commentId").asLong())).isEqualTo(1);
        assertThat(quotaOf(postId, "buyer01")).isZero();
        assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-17 닫힌 글은 만료돼도 승계하지 않고 상품만 ON_SALE")
    void closedPostDoesNotHandOver() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        comment(next, postId, "1 니트");
        closePost(seller, postId);
        testClock.setTo(DEADLINE);

        assertThat(claimExpiryScheduler.expireDueClaims()).isEqualTo(1);

        assertThat(claimStatus(claimId)).isEqualTo("EXPIRED");
        assertThat(productStatus(productId)).isEqualTo("ON_SALE");
        assertThat(currentClaimId(productId)).isNull();
        assertThat(claimCount(productId)).isEqualTo(1);
        assertThat(quotaOf(postId, "buyer01")).isZero();
        assertThat(quotaOf(postId, "buyer02")).isZero();
    }

    @Test
    @DisplayName("AC-9 [입금 안 됨]도 같은 승계: 응답 nextClaim에 새 당첨, 새 5분은 거절 시각부터")
    void rejectHandsOverToNext() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        Long nextCommentId = comment(next, postId, "1 니트").get("commentId").asLong();
        mockMvc.perform(claimAction(buyer, claimId, "report")).andExpect(status().isOk());
        LocalDateTime rejectedAt = FIXED_NOW.plusMinutes(30);
        testClock.setTo(rejectedAt);

        String response = mockMvc.perform(claimAction(seller, claimId, "reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.nextClaim.commentId").value(nextCommentId))
                .andExpect(jsonPath("$.nextClaim.buyerNickname").value("구매자2"))
                .andExpect(jsonPath("$.nextClaim.deadlineAt").value("2026-10-13T10:35:00"))
                .andReturn().getResponse().getContentAsString();

        Long nextClaimId = assertHandedOver(productId, claimId, nextCommentId, rejectedAt);
        assertThat(jsonMapper.readTree(response).get("nextClaim").get("claimId").asLong()).isEqualTo(nextClaimId);
        assertThat(quotaOf(postId, "buyer01")).isZero();
        assertThat(quotaOf(postId, "buyer02")).isEqualTo(1);
        // 다시 눌러도 승계는 한 번뿐
        mockMvc.perform(claimAction(seller, claimId, "reject")).andExpect(status().isOk());
        assertThat(claimCount(productId)).isEqualTo(2);
        assertThat(currentClaimId(productId)).isEqualTo(nextClaimId);
    }

    @Test
    @DisplayName("F-01 마감이 지났고 스케줄러가 안 돈 상태의 피드: WON과 지난 deadlineAt 그대로, 피드는 쓰기를 안 함")
    void feedDoesNotExpire() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long claimId = claimIdOf(comment(buyer, postId, "1 니트"));
        comment(next, postId, "1 니트");
        testClock.setTo(DEADLINE.plusSeconds(10));

        mockMvc.perform(get(POSTS + "/" + postId + "/feed?afterId=0").session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myClaims[0].status").value("WON"))
                .andExpect(jsonPath("$.myClaims[0].deadlineAt").value("2026-10-13T10:05:00"));

        assertThat(claimStatus(claimId)).isEqualTo("WON");
        assertThat(currentClaimId(productId)).isEqualTo(claimId);
        assertThat(claimCount(productId)).isEqualTo(1);
    }
}
