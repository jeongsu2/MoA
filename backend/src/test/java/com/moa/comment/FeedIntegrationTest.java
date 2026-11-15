package com.moa.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class FeedIntegrationTest extends ClaimTestSupport {

    private static final String WINNER_REAL_NAME = "김당첨";
    private static final String SELLER_ACCOUNT = "123-456-7890";

    @Test
    @DisplayName("F-01 실명은 판매자에게만, 계좌는 당첨자의 myClaims에만. 나머지는 키 자체가 없다")
    void realNameAndAccountVisibility() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession winner = signupBuyerWithRealName("buyer01", "구매자1", WINNER_REAL_NAME);
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트"), product("2", "치마")));
        JsonNode won = comment(winner, postId, "1 니트");
        Long claimId = won.get("claim").get("claimId").asLong();

        mockMvc.perform(get(feedUrl(postId, 0)).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").value("2026-10-13T10:00:00"))
                .andExpect(jsonPath("$.postStatus").value("OPEN"))
                .andExpect(jsonPath("$.products[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.products[0].claim.claimId").value(claimId))
                .andExpect(jsonPath("$.products[0].claim.commentId").value(won.get("commentId").asLong()))
                .andExpect(jsonPath("$.products[0].claim.buyerNickname").value("구매자1"))
                .andExpect(jsonPath("$.products[0].claim.buyerRealName").value(WINNER_REAL_NAME))
                .andExpect(jsonPath("$.products[0].claim.status").value("WON"))
                .andExpect(jsonPath("$.products[0].claim.deadlineAt").value("2026-10-13T10:05:00"))
                .andExpect(jsonPath("$.products[1].status").value("ON_SALE"))
                .andExpect(jsonPath("$.products[1].claim").isEmpty())
                .andExpect(jsonPath("$.myClaims").isEmpty());

        String winnerFeed = mockMvc.perform(get(feedUrl(postId, 0)).session(winner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].claim.buyerNickname").value("구매자1"))
                .andExpect(jsonPath("$.products[0].claim.buyerRealName").doesNotExist())
                .andExpect(jsonPath("$.myClaims.length()").value(1))
                .andExpect(jsonPath("$.myClaims[0].claimId").value(claimId))
                .andExpect(jsonPath("$.myClaims[0].status").value("WON"))
                .andExpect(jsonPath("$.myClaims[0].deadlineAt").value("2026-10-13T10:05:00"))
                .andExpect(jsonPath("$.myClaims[0].bankName").value("국민은행"))
                .andExpect(jsonPath("$.myClaims[0].accountNumber").value(SELLER_ACCOUNT))
                .andExpect(jsonPath("$.myClaims[0].accountHolder").value("홍길동"))
                .andExpect(jsonPath("$.myClaims[0].price").value(15000))
                .andExpect(jsonPath("$.myClaims[0].shippingFee").value(3500))
                .andExpect(jsonPath("$.myClaims[0].remoteShippingFee").value(6000))
                .andReturn().getResponse().getContentAsString();
        assertThat(winnerFeed).doesNotContain(WINNER_REAL_NAME);

        for (MockHttpSession viewer : new MockHttpSession[] {other, null}) {
            MvcResult result = mockMvc.perform(viewer == null ? get(feedUrl(postId, 0))
                            : get(feedUrl(postId, 0)).session(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.products[0].claim.buyerNickname").value("구매자1"))
                    .andExpect(jsonPath("$.products[0].claim.buyerRealName").doesNotExist())
                    .andExpect(jsonPath("$.myClaims").isEmpty())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain(WINNER_REAL_NAME).doesNotContain(SELLER_ACCOUNT);
        }
    }

    @Test
    @DisplayName("피드 댓글은 type, 닉네임, 상품 id, 작성 시각을 담고 afterId 이후만 온다")
    void commentsAfterId() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        Long productId = productIds(postId).get(0);
        Long normalId = comment(buyer, postId, "사이즈 몇인가요").get("commentId").asLong();
        Long orderId = comment(buyer, postId, "1 니트").get("commentId").asLong();

        mockMvc.perform(get(feedUrl(postId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(2))
                .andExpect(jsonPath("$.comments[0].commentId").value(normalId))
                .andExpect(jsonPath("$.comments[0].type").value("NORMAL"))
                .andExpect(jsonPath("$.comments[0].nickname").value("구매자1"))
                .andExpect(jsonPath("$.comments[0].content").value("사이즈 몇인가요"))
                .andExpect(jsonPath("$.comments[0].productId").isEmpty())
                .andExpect(jsonPath("$.comments[0].parentId").isEmpty())
                .andExpect(jsonPath("$.comments[0].createdAt").value("2026-10-13T10:00:00"))
                .andExpect(jsonPath("$.comments[1].type").value("ORDER"))
                .andExpect(jsonPath("$.comments[1].productId").value(productId))
                .andExpect(jsonPath("$.hasMore").value(false));

        // 겹쳐 읽기 구간(최근 5초)이 지난 뒤라 afterId 이후만 온다
        testClock.advance(Duration.ofSeconds(10));
        mockMvc.perform(get(feedUrl(postId, normalId)))
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].commentId").value(orderId));
        mockMvc.perform(get(feedUrl(postId, orderId)))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andExpect(jsonPath("$.products[0].status").value("RESERVED"));
    }

    @Test
    @DisplayName("afterId보다 작은 id라도 최근 5초 안에 만든 댓글은 겹쳐 준다. 늦게 커밋된 댓글을 놓치지 않게")
    void recentCommentsOverlap() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        testClock.setTo(FIXED_NOW.minusMinutes(10));
        Long oldId = comment(buyer, postId, "예전 댓글").get("commentId").asLong();
        testClock.setTo(FIXED_NOW);
        Long recentId = comment(buyer, postId, "최근 댓글").get("commentId").asLong();
        Long lastId = comment(buyer, postId, "마지막 댓글").get("commentId").asLong();

        // 프론트가 lastId까지 받았는데 recentId가 그 뒤에 커밋된 상황. 5초 안이라 다시 온다
        testClock.setTo(FIXED_NOW.plusSeconds(5));
        JsonNode overlapped = readFeed(postId, lastId);
        List<Long> ids = new ArrayList<>();
        overlapped.get("comments").forEach(comment -> ids.add(comment.get("commentId").asLong()));
        assertThat(ids).containsExactly(recentId, lastId).doesNotContain(oldId);

        // 5초가 지나면 afterId 이후만
        testClock.setTo(FIXED_NOW.plusSeconds(6));
        assertThat(readFeed(postId, lastId).get("comments").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("F-02 댓글 150개: 첫 응답 100개와 hasMore=true, 이어서 50개와 false. 피드는 아무 행도 안 바꾼다")
    void pagingAndReadOnly() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long postId = createOpenPost(seller, List.of(product("1", "니트")));
        comment(buyer, postId, "1 니트");
        Long buyerId = jdbcTemplate.queryForObject("select id from users where login_id = 'buyer01'", Long.class);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 149; i++) {
            rows.add(new Object[] {postId, buyerId, "댓글 " + i, Timestamp.valueOf(FIXED_NOW)});
        }
        jdbcTemplate.batchUpdate("insert into comment (post_id, user_id, type, content, created_at)"
                + " values (?, ?, 'NORMAL', ?, ?)", rows);
        List<Map<String, Object>> productsBefore = jdbcTemplate.queryForList("select * from product order by id");
        List<Map<String, Object>> claimsBefore = jdbcTemplate.queryForList("select * from claim order by id");
        // 마감이 지난 시각에 불러도 만료 같은 쓰기는 일으키지 않는다 (04 문서 3장)
        testClock.setTo(FIXED_NOW.plusMinutes(10));

        JsonNode first = readFeed(postId, 0);
        assertThat(first.get("comments").size()).isEqualTo(100);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        long lastId = first.get("comments").get(99).get("commentId").asLong();

        JsonNode second = readFeed(postId, lastId);
        assertThat(second.get("comments").size()).isEqualTo(50);
        assertThat(second.get("hasMore").asBoolean()).isFalse();
        assertThat(second.get("comments").get(0).get("commentId").asLong()).isGreaterThan(lastId);
        assertThat(second.get("serverTime").asString()).isEqualTo("2026-10-13T10:10:00");

        assertThat(jdbcTemplate.queryForList("select * from product order by id")).isEqualTo(productsBefore);
        assertThat(jdbcTemplate.queryForList("select * from claim order by id")).isEqualTo(claimsBefore);
    }

    @Test
    @DisplayName("DRAFT 글 피드는 작성자 말고는 404, 없는 글도 404")
    void draftFeedIsHidden() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Long draftId = createDraftPost(seller, List.of(product("1", "니트")));

        mockMvc.perform(get(feedUrl(draftId, 0)).session(buyer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(feedUrl(draftId, 0)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(feedUrl(draftId + 1000, 0)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(feedUrl(draftId, 0)).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postStatus").value("DRAFT"));
    }

    private String feedUrl(Long postId, long afterId) {
        return POSTS + "/" + postId + "/feed?afterId=" + afterId;
    }

    private JsonNode readFeed(Long postId, long afterId) throws Exception {
        String response = mockMvc.perform(get(feedUrl(postId, afterId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }

    private MockHttpSession signupBuyerWithRealName(String loginId, String nickname, String realName)
            throws Exception {
        Map<String, Object> body = buyerBody(loginId, nickname);
        body.put("realName", realName);
        MvcResult result = mockMvc.perform(jsonPost("/api/auth/signup/buyer", body))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
