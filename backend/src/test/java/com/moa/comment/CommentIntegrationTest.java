package com.moa.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.JsonNode;

class CommentIntegrationTest extends ClaimTestSupport {

    @Test
    @DisplayName("M-03 정확한 댓글은 ORDER와 상품 id, 틀린 댓글은 NORMAL과 null. 둘 다 201")
    void orderAndNormalComment() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1-2", "보라색에코백")));
        Long productId = productIds(postId).get(0);

        JsonNode normal = comment(buyer, postId, "1-2 보라색에코백 구매할게요");
        assertThat(normal.get("type").asString()).isEqualTo("NORMAL");
        assertThat(normal.get("productId").isNull()).isTrue();
        assertThat(normal.get("won").asBoolean()).isFalse();
        assertThat(normal.get("claim").isNull()).isTrue();

        JsonNode order = comment(buyer, postId, " 1-2. 보라색 에코백 ");
        assertThat(order.get("type").asString()).isEqualTo("ORDER");
        assertThat(order.get("productId").asLong()).isEqualTo(productId);
        assertThat(order.get("won").asBoolean()).isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select type, product_id, content from comment where id = ?", order.get("commentId").asLong());
        assertThat(row.get("type")).isEqualTo("ORDER");
        assertThat(row.get("product_id")).isEqualTo(productId);
        // 원문은 그대로 저장한다
        assertThat(row.get("content")).isEqualTo(" 1-2. 보라색 에코백 ");
        assertThat(jdbcTemplate.queryForObject("select product_id from comment where id = ?", Long.class,
                normal.get("commentId").asLong())).isNull();
    }

    @Test
    @DisplayName("M-01 정규화: 이름 안 공백, 번호 뒤 점, 영문 대소문자를 무시하고 당첨")
    void normalizedMatch() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "빈티지 원피스"), product("2", "Oxford Shirt")));

        assertThat(comment(buyer, postId, "1. 빈티지원피스").get("won").asBoolean()).isTrue();
        assertThat(comment(buyer, postId, "2oxford SHIRT").get("won").asBoolean()).isTrue();
        assertThat(productStatus(productIds(postId).get(0))).isEqualTo("RESERVED");
        assertThat(productStatus(productIds(postId).get(1))).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("M-04 판매자가 자기 글에 쓴 정확한 댓글은 NORMAL, 다른 판매자 글에는 당첨")
    void sellerOwnPostIsNormal() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession otherSeller = signupSeller("seller02", "판매자2");
        Long myPostId = createOpenPost(seller, List.of(product("1", "니트")));
        Long otherPostId = createOpenPost(otherSeller, List.of(product("1", "니트")));

        JsonNode own = comment(seller, myPostId, "1 니트");
        assertThat(own.get("type").asString()).isEqualTo("NORMAL");
        assertThat(own.get("won").asBoolean()).isFalse();
        assertThat(productStatus(productIds(myPostId).get(0))).isEqualTo("ON_SALE");
        assertThat(countRows("claim")).isZero();

        JsonNode other = comment(seller, otherPostId, "1 니트");
        assertThat(other.get("type").asString()).isEqualTo("ORDER");
        assertThat(other.get("won").asBoolean()).isTrue();
        assertThat(quotaOf(otherPostId, "seller01")).isEqualTo(1);
    }

    @Test
    @DisplayName("M-05 비로그인 401, DRAFT 글 404, 없는 글 404, 빈 댓글과 길이 초과 400. 댓글 행 안 생김")
    void rejectInvalidComments() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long draftId = createDraftPost(seller, List.of(product("1", "니트")));
        Long openId = createOpenPost(seller, List.of(product("1", "니트")));

        mockMvc.perform(commentRequest(null, openId, "1 니트"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(commentRequest(buyer, draftId, "1 니트"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(commentRequest(buyer, draftId + 1000, "1 니트"))
                .andExpect(status().isNotFound());
        mockMvc.perform(commentRequest(buyer, openId, "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
        mockMvc.perform(commentRequest(buyer, openId, "가".repeat(Comment.MAX_CONTENT_LENGTH + 1)))
                .andExpect(status().isBadRequest());

        assertThat(countRows("comment")).isZero();
        assertThat(productStatus(productIds(draftId).get(0))).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("닫힌 글에는 댓글을 쓸 수 없다 409")
    void rejectCommentOnClosedPost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/close")).session(seller))
                .andExpect(status().isOk());

        mockMvc.perform(commentRequest(buyer, postId, "1 니트"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_CLOSED"));

        assertThat(countRows("comment")).isZero();
        assertThat(countRows("claim")).isZero();
    }

    @Test
    @DisplayName("W-01 고정 시계 10:00:00, 보낸 시각은 무시. claim WON, 마감 10:05:00, 상품 RESERVED, quota 1")
    void winWithServerClock() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "1 니트");
        body.put("wonAt", "2020-01-01T00:00:00");
        body.put("createdAt", "2020-01-01T00:00:00");

        String response = mockMvc.perform(jsonPost(POSTS + "/" + postId + "/comments", body).session(buyer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.won").value(true))
                .andExpect(jsonPath("$.claim.status").value("WON"))
                .andExpect(jsonPath("$.claim.deadlineAt").value("2026-10-13T10:05:00"))
                .andReturn().getResponse().getContentAsString();
        JsonNode result = jsonMapper.readTree(response);
        Long claimId = result.get("claim").get("claimId").asLong();

        Map<String, Object> claim = jdbcTemplate.queryForMap(
                "select status, won_at, deadline_at, comment_id, product_id from claim where id = ?", claimId);
        assertThat(claim.get("status")).isEqualTo("WON");
        assertThat(toLocal(claim.get("won_at"))).isEqualTo(IntegrationTestSupport.FIXED_NOW);
        assertThat(toLocal(claim.get("deadline_at"))).isEqualTo(IntegrationTestSupport.FIXED_NOW.plusMinutes(5));
        assertThat(claim.get("comment_id")).isEqualTo(result.get("commentId").asLong());
        assertThat(toLocal(jdbcTemplate.queryForObject("select created_at from comment where id = ?",
                Timestamp.class, result.get("commentId").asLong()))).isEqualTo(IntegrationTestSupport.FIXED_NOW);
        assertThat(productStatus(productId)).isEqualTo("RESERVED");
        assertThat(currentClaimId(productId)).isEqualTo(claimId);
        assertThat(quotaOf(postId, "buyer01")).isEqualTo(1);
    }

    @Test
    @DisplayName("W-02 RESERVED, SOLD 상품에 정확한 댓글은 ORDER로 남고 당첨 안 됨, 상품 그대로")
    void exactCommentOnUnavailableProduct() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession first = signupBuyer("buyer01", "구매자1");
        MockHttpSession second = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트"), product("2", "치마")));
        Long reservedId = productIds(postId).get(0);
        Long soldId = productIds(postId).get(1);
        Long winningClaimId = comment(first, postId, "1 니트").get("claim").get("claimId").asLong();
        // 판매 완료는 #30에서 생긴다. 여기서는 상태만 바꿔 둔다
        jdbcTemplate.update("update product set status = 'SOLD' where id = ?", soldId);

        JsonNode waiting = comment(second, postId, "1 니트");
        assertThat(waiting.get("type").asString()).isEqualTo("ORDER");
        assertThat(waiting.get("productId").asLong()).isEqualTo(reservedId);
        assertThat(waiting.get("won").asBoolean()).isFalse();
        assertThat(waiting.get("claim").isNull()).isTrue();

        JsonNode sold = comment(second, postId, "2 치마");
        assertThat(sold.get("type").asString()).isEqualTo("ORDER");
        assertThat(sold.get("won").asBoolean()).isFalse();

        assertThat(currentClaimId(reservedId)).isEqualTo(winningClaimId);
        assertThat(productStatus(soldId)).isEqualTo("SOLD");
        assertThat(countRows("claim")).isEqualTo(1);
        assertThat(quotaOf(postId, "buyer02")).isZero();
    }

    @Test
    @DisplayName("Q-01 5개 당첨 중인 사람의 6번째 정확 댓글은 ORDER만, 상품은 ON_SALE. 다음 사람이 당첨")
    void sixthOrderIsSkipped() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession next = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, numberedProducts(6));
        for (int i = 1; i <= 5; i++) {
            assertThat(comment(buyer, postId, i + " 상품" + i).get("won").asBoolean()).isTrue();
        }
        Long sixthId = productIds(postId).get(5);

        JsonNode sixth = comment(buyer, postId, "6 상품6");
        assertThat(sixth.get("type").asString()).isEqualTo("ORDER");
        assertThat(sixth.get("won").asBoolean()).isFalse();
        assertThat(productStatus(sixthId)).isEqualTo("ON_SALE");
        assertThat(currentClaimId(sixthId)).isNull();
        assertThat(quotaOf(postId, "buyer01")).isEqualTo(5);

        assertThat(comment(next, postId, "6 상품6").get("won").asBoolean()).isTrue();
        assertThat(productStatus(sixthId)).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("1인 5개는 글마다 따로 센다 (D-13)")
    void quotaIsPerPost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long firstPostId = createOpenPost(seller, numberedProducts(5));
        Long secondPostId = createOpenPost(seller, numberedProducts(1));
        for (int i = 1; i <= 5; i++) {
            comment(buyer, firstPostId, i + " 상품" + i);
        }

        assertThat(comment(buyer, secondPostId, "1 상품1").get("won").asBoolean()).isTrue();
        assertThat(quotaOf(firstPostId, "buyer01")).isEqualTo(5);
        assertThat(quotaOf(secondPostId, "buyer01")).isEqualTo(1);
    }

    private LocalDateTime toLocal(Object timestamp) {
        if (timestamp instanceof LocalDateTime local) {
            return local;
        }
        return ((Timestamp) timestamp).toLocalDateTime();
    }
}
