package com.moa.deposit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.JsonNode;

// 05 테스트 계획 1~3장의 순차 시나리오. 동시 요청은 DepositConcurrencyTest
class DepositIntegrationTest extends DepositTestSupport {

    @Test
    @DisplayName("R-01 10:04:59 신고: REPORTED, 주문 스냅샷 1개, DEPOSIT_REPORT 대댓글 1개. 본문에 끼운 금액과 주소는 무시")
    void reportJustBeforeDeadline() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        testClock.setTo(FIXED_NOW.plusMinutes(4).plusSeconds(59));

        Map<String, Object> forged = new LinkedHashMap<>(address("X", "63000"));
        forged.put("totalAmount", 1);
        forged.put("shippingFee", 0);
        mockMvc.perform(claimAction(buyer, won.claimId(), "report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(forged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(won.claimId()))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.reportedAt").value("2026-10-13T10:04:59"))
                .andExpect(jsonPath("$.order.totalAmount").value(PRICE + 3500))
                .andExpect(jsonPath("$.order.price").value(PRICE))
                .andExpect(jsonPath("$.order.shippingFee").value(3500))
                .andExpect(jsonPath("$.order.zipCode").value("06236"))
                .andExpect(jsonPath("$.order.address1").value("서울시 강남구 테헤란로 1"));

        assertThat(claimStatus(won.claimId())).isEqualTo("REPORTED");
        assertThat(jdbcTemplate.queryForObject("select reported_at from claim where id = ?", Timestamp.class,
                won.claimId()).toLocalDateTime()).isEqualTo(FIXED_NOW.plusMinutes(4).plusSeconds(59));
        assertThat(orderCount(won.claimId())).isEqualTo(1);
        Map<String, Object> order = orderRow(won.claimId());
        assertThat(order).containsEntry("recipient_name", "홍길동")
                .containsEntry("recipient_phone", "01012345678")
                .containsEntry("zip_code", "06236")
                .containsEntry("address1", "서울시 강남구 테헤란로 1")
                .containsEntry("address2", "101호")
                .containsEntry("delivery_note", "공동현관 1234")
                .containsEntry("price", PRICE)
                .containsEntry("shipping_fee", 3500)
                .containsEntry("total_amount", PRICE + 3500);
        assertThat(replyCount(won.commentId(), "DEPOSIT_REPORT")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select content from comment where parent_id = ? and type = ?",
                String.class, won.commentId(), "DEPOSIT_REPORT")).isEqualTo("입금완료");
        // 신고만으로는 상품이 계속 예약 상태다
        assertThat(productStatus(won.productId())).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("R-02 정확히 10:05:00 신고는 409 입금 시간이 지났습니다. 스케줄러가 안 돌았어도 WON 그대로, 주문과 댓글 없음")
    void reportAtDeadlineFails() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        testClock.setTo(FIXED_NOW.plusMinutes(5));

        mockMvc.perform(claimAction(buyer, won.claimId(), "report"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOSIT_DEADLINE_PASSED"))
                .andExpect(jsonPath("$.message").value("입금 시간이 지났습니다"));

        testClock.setTo(FIXED_NOW.plusMinutes(5).plusSeconds(1));
        mockMvc.perform(claimAction(buyer, won.claimId(), "report"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOSIT_DEADLINE_PASSED"));

        assertThat(claimStatus(won.claimId())).isEqualTo("WON");
        assertThat(orderCount(won.claimId())).isZero();
        assertThat(replyCount(won.commentId(), "DEPOSIT_REPORT")).isZero();
    }

    @Test
    @DisplayName("AC-6 이미 REPORTED인 내 claim을 다시 신고: 200과 같은 주문, 주문과 댓글은 1개")
    void reportAgainReturnsSameOrder() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        JsonNode first = reportOk(buyer, won.claimId());

        // 신고 뒤에는 5분이 지나도 같은 응답이다
        testClock.setTo(FIXED_NOW.plusMinutes(10));
        JsonNode second = reportOk(buyer, won.claimId());

        assertThat(second).isEqualTo(first);
        assertThat(orderCount(won.claimId())).isEqualTo(1);
        assertThat(replyCount(won.commentId(), "DEPOSIT_REPORT")).isEqualTo(1);
    }

    @Test
    @DisplayName("R-04 남의 claim과 없는 claim 신고는 같은 404. 남의 claim은 그대로")
    void reportOthersClaimIsNotFound() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        Won won = win(seller, buyer);

        String othersBody = mockMvc.perform(claimAction(other, won.claimId(), "report"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(claimAction(other, won.claimId() + 1000, "report"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        // 판매자도 남의 당첨을 대신 신고하지 못한다
        mockMvc.perform(claimAction(seller, won.claimId(), "report"))
                .andExpect(status().isNotFound());

        assertThat(othersBody).isEqualTo(missingBody);
        assertThat(claimStatus(won.claimId())).isEqualTo("WON");
        assertThat(orderCount(won.claimId())).isZero();
    }

    @Test
    @DisplayName("A-01 기본을 B로 바꾼 뒤 신고하면 주문은 B. 이후 기본을 C로 바꾸고 B를 고쳐도 주문 주소 그대로")
    void orderKeepsAddressSnapshot() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        Long addressB = addAddress(buyer, address("B", "11111"));
        Long addressC = addAddress(buyer, address("C", "22222"));
        mockMvc.perform(changeDefaultRequest(buyer, addressB)).andExpect(status().isOk());

        reportOk(buyer, won.claimId());
        Map<String, Object> before = orderRow(won.claimId());
        mockMvc.perform(changeDefaultRequest(buyer, addressC)).andExpect(status().isOk());
        mockMvc.perform(changeAddressRequest(buyer, addressB, address("Z", "33333"))).andExpect(status().isOk());

        Map<String, Object> after = orderRow(won.claimId());
        assertThat(after).isEqualTo(before);
        assertThat(after).containsEntry("recipient_name", "받는B")
                .containsEntry("recipient_phone", "01022222222")
                .containsEntry("zip_code", "11111")
                .containsEntry("address1", "B시 B구 1")
                .containsEntry("address2", "B동 101호")
                .containsEntry("delivery_note", "B 문 앞");
    }

    @Test
    @DisplayName("A-04 기본 배송지가 제주면 배송비 6000, 합계 21000. 서울은 3500, 18500")
    void jejuShippingFee() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession jejuBuyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession seoulBuyer = signupBuyer("buyer02", "구매자2");
        Long jeju = addAddress(jejuBuyer, address("J", "63309"));
        mockMvc.perform(changeDefaultRequest(jejuBuyer, jeju)).andExpect(status().isOk());

        Won jejuWon = win(seller, jejuBuyer);
        Won seoulWon = win(seller, seoulBuyer);
        reportOk(jejuBuyer, jejuWon.claimId());
        reportOk(seoulBuyer, seoulWon.claimId());

        assertThat(orderRow(jejuWon.claimId())).containsEntry("shipping_fee", 6000)
                .containsEntry("total_amount", PRICE + 6000);
        assertThat(orderRow(seoulWon.claimId())).containsEntry("shipping_fee", 3500)
                .containsEntry("total_amount", PRICE + 3500);
    }

    @Test
    @DisplayName("A-04 배송비는 코드 상수가 아니라 글에 저장된 값 (4000, 7000으로 바꾸면 그 값)")
    void shippingFeeComesFromPost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession jejuBuyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession seoulBuyer = signupBuyer("buyer02", "구매자2");
        Long jeju = addAddress(jejuBuyer, address("J", "63000"));
        mockMvc.perform(changeDefaultRequest(jejuBuyer, jeju)).andExpect(status().isOk());
        Long postId = createOpenPost(seller, List.of(product("1", "니트"), product("2", "치마")));
        jdbcTemplate.update("update sale_post set shipping_fee = 4000, remote_shipping_fee = 7000 where id = ?",
                postId);
        Long jejuClaim = comment(jejuBuyer, postId, "1 니트").get("claim").get("claimId").asLong();
        Long seoulClaim = comment(seoulBuyer, postId, "2 치마").get("claim").get("claimId").asLong();

        assertThat(reportOk(jejuBuyer, jejuClaim).get("order").get("totalAmount").asInt()).isEqualTo(PRICE + 7000);
        assertThat(reportOk(seoulBuyer, seoulClaim).get("order").get("shippingFee").asInt()).isEqualTo(4000);
    }

    @Test
    @DisplayName("S-01 판매자 입금확인: CONFIRMED, 상품 SOLD, 당첨 댓글에 감사 SYSTEM 대댓글 1개. 다시 눌러도 200, 댓글 1개")
    void confirm() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        reportOk(buyer, won.claimId());
        // 판매자 확인은 시간 제한이 없다 (D-07)
        testClock.setTo(FIXED_NOW.plusHours(3));

        mockMvc.perform(claimAction(seller, won.claimId(), "confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(won.claimId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-10-13T13:00:00"));
        testClock.setTo(FIXED_NOW.plusHours(4));
        mockMvc.perform(claimAction(seller, won.claimId(), "confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-10-13T13:00:00"));

        assertThat(claimStatus(won.claimId())).isEqualTo("CONFIRMED");
        assertThat(productStatus(won.productId())).isEqualTo("SOLD");
        assertThat(currentClaimId(won.productId())).isNull();
        assertThat(replyCount(won.commentId(), "SYSTEM")).isEqualTo(1);
        Map<String, Object> thanks = jdbcTemplate.queryForMap(
                "select m.content, u.login_id from comment m join users u on u.id = m.user_id"
                        + " where m.parent_id = ? and m.type = 'SYSTEM'", won.commentId());
        assertThat(thanks).containsEntry("content", "구매감사합니다. 예쁘게 포장해서 전달드릴게요 💓")
                .containsEntry("login_id", "seller01");
        // 확정도 구매 수에 들어간다
        assertThat(quotaOf(won.postId(), "buyer01")).isEqualTo(1);
    }

    @Test
    @DisplayName("S-03 신고 전 WON을 입금확인하면 409, 상태와 댓글 그대로")
    void confirmBeforeReportConflicts() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);

        mockMvc.perform(claimAction(seller, won.claimId(), "confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATUS_CONFLICT"));
        mockMvc.perform(claimAction(seller, won.claimId(), "reject"))
                .andExpect(status().isConflict());

        assertThat(claimStatus(won.claimId())).isEqualTo("WON");
        assertThat(productStatus(won.productId())).isEqualTo("RESERVED");
        assertThat(replyCount(won.commentId(), "SYSTEM")).isZero();
    }

    @Test
    @DisplayName("S-04 다른 판매자, 당첨자 본인, 다른 구매자의 입금확인과 입금 안 됨은 403, 비로그인은 401. 아무것도 안 바뀜")
    void onlyPostSellerCanConfirmOrReject() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession otherSeller = signupSeller("seller02", "판매자2");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession other = signupBuyer("buyer02", "구매자2");
        Won won = win(seller, buyer);
        reportOk(buyer, won.claimId());

        for (MockHttpSession session : new MockHttpSession[] {otherSeller, buyer, other}) {
            for (String action : new String[] {"confirm", "reject"}) {
                mockMvc.perform(claimAction(session, won.claimId(), action))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
        mockMvc.perform(claimAction(null, won.claimId(), "confirm")).andExpect(status().isUnauthorized());
        mockMvc.perform(claimAction(null, won.claimId(), "reject")).andExpect(status().isUnauthorized());
        // 없는 claim은 판매자에게도 404
        mockMvc.perform(claimAction(seller, won.claimId() + 1000, "confirm")).andExpect(status().isNotFound());

        assertThat(claimStatus(won.claimId())).isEqualTo("REPORTED");
        assertThat(productStatus(won.productId())).isEqualTo("RESERVED");
        assertThat(currentClaimId(won.productId())).isEqualTo(won.claimId());
        assertThat(replyCount(won.commentId(), "SYSTEM")).isZero();
        assertThat(quotaOf(won.postId(), "buyer01")).isEqualTo(1);
    }

    @Test
    @DisplayName("S-06 입금 안 됨: REJECTED, quota -1, 상품 ON_SALE, 주문 행은 남음. 다시 눌러도 200. 거절된 구매자는 새 댓글로 재도전")
    void reject() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        reportOk(buyer, won.claimId());
        testClock.setTo(FIXED_NOW.plusMinutes(30));

        mockMvc.perform(claimAction(seller, won.claimId(), "reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(won.claimId()))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.nextClaim").isEmpty());
        mockMvc.perform(claimAction(seller, won.claimId(), "reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        // 거절 뒤 입금확인은 진 쪽이라 409
        mockMvc.perform(claimAction(seller, won.claimId(), "confirm"))
                .andExpect(status().isConflict());

        assertThat(claimStatus(won.claimId())).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject("select ended_at from claim where id = ?", Timestamp.class,
                won.claimId()).toLocalDateTime()).isEqualTo(FIXED_NOW.plusMinutes(30));
        assertThat(quotaOf(won.postId(), "buyer01")).isZero();
        assertThat(productStatus(won.productId())).isEqualTo("ON_SALE");
        assertThat(currentClaimId(won.productId())).isNull();
        assertThat(orderCount(won.claimId())).isEqualTo(1);

        // D-09 재도전. 새 댓글이 새 claim을 만든다
        JsonNode retry = comment(buyer, won.postId(), "1 니트");
        assertThat(retry.get("won").asBoolean()).isTrue();
        assertThat(claimCount(won.productId())).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-11 피드 myClaims: 신고 전에는 order 키가 없고, 신고 후 금액, 배송비, 주소 요약이 붙는다")
    void feedShowsOrderAfterReport() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        Won won = win(seller, buyer);
        String feedUrl = POSTS + "/" + won.postId() + "/feed";

        mockMvc.perform(get(feedUrl).session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myClaims[0].status").value("WON"))
                .andExpect(jsonPath("$.myClaims[0].order").doesNotExist());

        reportOk(buyer, won.claimId());
        mockMvc.perform(get(feedUrl).session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myClaims[0].status").value("REPORTED"))
                .andExpect(jsonPath("$.myClaims[0].order.totalAmount").value(PRICE + 3500))
                .andExpect(jsonPath("$.myClaims[0].order.price").value(PRICE))
                .andExpect(jsonPath("$.myClaims[0].order.shippingFee").value(3500))
                .andExpect(jsonPath("$.myClaims[0].order.zipCode").value("06236"))
                .andExpect(jsonPath("$.myClaims[0].order.address1").value("서울시 강남구 테헤란로 1"))
                // 받는 사람과 연락처는 판매자 주문 목록에만
                .andExpect(jsonPath("$.myClaims[0].order.recipientPhone").doesNotExist());
        // DEPOSIT_REPORT 댓글은 당첨 댓글의 대댓글로 피드에 보인다
        mockMvc.perform(get(feedUrl).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[1].type").value("DEPOSIT_REPORT"))
                .andExpect(jsonPath("$.comments[1].parentId").value(won.commentId()))
                .andExpect(jsonPath("$.myClaims").isEmpty());
    }

    @Test
    @DisplayName("P-01 주문 목록은 그 글의 판매자만. 신고 전 WON은 목록에 없고 주소, 연락처가 안 나옴")
    void sellerOrders() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession otherSeller = signupSeller("seller02", "판매자2");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        MockHttpSession waiting = signupBuyer("buyer02", "구매자2");
        Long postId = createOpenPost(seller, List.of(product("1", "니트"), product("2", "치마")));
        Long reportedClaim = comment(buyer, postId, "1 니트").get("claim").get("claimId").asLong();
        comment(waiting, postId, "2 치마");
        testClock.setTo(FIXED_NOW.plusMinutes(1));
        reportOk(buyer, reportedClaim);
        String ordersUrl = POSTS + "/" + postId + "/orders";

        mockMvc.perform(get(ordersUrl).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].claimId").value(reportedClaim))
                .andExpect(jsonPath("$[0].status").value("REPORTED"))
                .andExpect(jsonPath("$[0].itemNo").value("1"))
                .andExpect(jsonPath("$[0].productName").value("니트"))
                .andExpect(jsonPath("$[0].buyerNickname").value("구매자1"))
                .andExpect(jsonPath("$[0].buyerRealName").value("홍길동"))
                .andExpect(jsonPath("$[0].totalAmount").value(PRICE + 3500))
                .andExpect(jsonPath("$[0].shippingFee").value(3500))
                .andExpect(jsonPath("$[0].recipientName").value("홍길동"))
                .andExpect(jsonPath("$[0].recipientPhone").value("01012345678"))
                .andExpect(jsonPath("$[0].zipCode").value("06236"))
                .andExpect(jsonPath("$[0].address2").value("101호"))
                .andExpect(jsonPath("$[0].deliveryNote").value("공동현관 1234"))
                .andExpect(jsonPath("$[0].reportedAt").value("2026-10-13T10:01:00"));
        mockMvc.perform(get(ordersUrl).session(otherSeller)).andExpect(status().isForbidden());
        mockMvc.perform(get(ordersUrl).session(buyer)).andExpect(status().isForbidden());
    }
}
