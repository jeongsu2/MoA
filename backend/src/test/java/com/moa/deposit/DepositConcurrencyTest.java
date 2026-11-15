package com.moa.deposit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

// 동시 요청은 CountDownLatch로 같이 출발하고 10번 반복한다 (05 테스트 계획 0장, notes 8번).
// 기대한 상태 코드(200 또는 409) 말고 500이나 데드락이 하나라도 나오면 실패한다
class DepositConcurrencyTest extends DepositTestSupport {

    private static final int REPEAT = 10;
    private static final int THREADS = 20;

    @Test
    @DisplayName("R-03 같은 claim 신고 20건 동시: 전부 200, 응답 주문이 모두 같음, 주문 1개, DEPOSIT_REPORT 1개 (10번 반복)")
    void concurrentReports() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");

        for (int round = 0; round < REPEAT; round++) {
            Won won = win(seller, buyer);
            List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                requests.add(claimAction(buyer, won.claimId(), "report"));
            }

            List<MockHttpServletResponse> responses = sendAtTheSameTime(requests);

            assertThat(statuses(responses)).allMatch(status -> status == 200);
            JsonNode first = jsonMapper.readTree(body(responses.get(0)));
            assertThat(first.get("status").asString()).isEqualTo("REPORTED");
            assertThat(first.get("order").get("totalAmount").asInt()).isEqualTo(PRICE + 3500);
            for (MockHttpServletResponse response : responses) {
                assertThat(jsonMapper.readTree(body(response))).isEqualTo(first);
            }
            assertThat(orderCount(won.claimId())).isEqualTo(1);
            assertThat(replyCount(won.commentId(), "DEPOSIT_REPORT")).isEqualTo(1);
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("S-02 같은 claim 입금확인 20건 동시: 전부 200, 감사 댓글 정확히 1개 (10번 반복)")
    void concurrentConfirms() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");

        for (int round = 0; round < REPEAT; round++) {
            Won won = win(seller, buyer);
            reportOk(buyer, won.claimId());
            List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                requests.add(claimAction(seller, won.claimId(), "confirm"));
            }

            List<MockHttpServletResponse> responses = sendAtTheSameTime(requests);

            assertThat(statuses(responses)).allMatch(status -> status == 200);
            for (MockHttpServletResponse response : responses) {
                assertThat(jsonMapper.readTree(body(response)).get("status").asString()).isEqualTo("CONFIRMED");
            }
            assertThat(replyCount(won.commentId(), "SYSTEM")).isEqualTo(1);
            assertThat(productStatus(won.productId())).isEqualTo("SOLD");
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("S-05 같은 claim에 입금확인 10건과 입금 안 됨 10건 동시: 한 종류만 200, 다른 종류는 409 (10번 반복)")
    void confirmAndRejectAtTheSameTime() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");
        int half = THREADS / 2;

        for (int round = 0; round < REPEAT; round++) {
            Won won = win(seller, buyer);
            reportOk(buyer, won.claimId());
            List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
            for (int i = 0; i < half; i++) {
                requests.add(claimAction(seller, won.claimId(), "confirm"));
                requests.add(claimAction(seller, won.claimId(), "reject"));
            }

            List<Integer> statuses = statuses(sendAtTheSameTime(requests));

            List<Integer> confirmStatuses = new ArrayList<>();
            List<Integer> rejectStatuses = new ArrayList<>();
            for (int i = 0; i < statuses.size(); i++) {
                if (i % 2 == 0) {
                    confirmStatuses.add(statuses.get(i));
                } else {
                    rejectStatuses.add(statuses.get(i));
                }
            }
            if (claimStatus(won.claimId()).equals("CONFIRMED")) {
                assertThat(confirmStatuses).allMatch(status -> status == 200);
                assertThat(rejectStatuses).allMatch(status -> status == 409);
                assertThat(productStatus(won.productId())).isEqualTo("SOLD");
                assertThat(replyCount(won.commentId(), "SYSTEM")).isEqualTo(1);
                assertThat(quotaOf(won.postId(), "buyer01")).isEqualTo(1);
            } else {
                assertThat(claimStatus(won.claimId())).isEqualTo("REJECTED");
                assertThat(rejectStatuses).allMatch(status -> status == 200);
                assertThat(confirmStatuses).allMatch(status -> status == 409);
                assertThat(productStatus(won.productId())).isEqualTo("ON_SALE");
                assertThat(replyCount(won.commentId(), "SYSTEM")).isZero();
                assertThat(quotaOf(won.postId(), "buyer01")).isZero();
            }
            assertClaimInvariants();
        }
    }

    @Test
    @DisplayName("A-02 기본을 B로 바꾸는 요청과 신고 동시: 주문 주소는 A 전체이거나 B 전체, 섞이지 않음 (10번 반복)")
    void reportWhileChangingDefault() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");

        for (int round = 0; round < REPEAT; round++) {
            MockHttpSession buyer = signupBuyer("buyer" + (100 + round), "구매자" + round);
            Long addressA = addAddress(buyer, address("A", "11111"));
            Long addressB = addAddress(buyer, address("B", "63000"));
            assertThat(mockMvc.perform(changeDefaultRequest(buyer, addressA)).andReturn().getResponse().getStatus())
                    .isEqualTo(200);
            Won won = win(seller, buyer);

            List<Integer> statuses = statuses(sendAtTheSameTime(List.of(
                    claimAction(buyer, won.claimId(), "report"),
                    changeDefaultRequest(buyer, addressB))));

            assertThat(statuses).containsExactly(200, 200);
            Map<String, Object> order = orderRow(won.claimId());
            String tag = order.get("recipient_name").equals("받는A") ? "A" : "B";
            Map<String, Object> expected = address(tag, tag.equals("A") ? "11111" : "63000");
            assertThat(order).containsEntry("recipient_name", expected.get("recipientName"))
                    .containsEntry("recipient_phone", expected.get("recipientPhone"))
                    .containsEntry("zip_code", expected.get("zipCode"))
                    .containsEntry("address1", expected.get("address1"))
                    .containsEntry("address2", expected.get("address2"))
                    .containsEntry("delivery_note", expected.get("deliveryNote"))
                    // 배송비도 같은 주소로 계산한다 (B는 제주)
                    .containsEntry("shipping_fee", tag.equals("A") ? 3500 : 6000);
            assertClaimInvariants();
        }
    }

    private List<MockHttpServletResponse> sendAtTheSameTime(List<MockHttpServletRequestBuilder> requests)
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

            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                MockHttpServletResponse response = future.get(60, TimeUnit.SECONDS);
                // 500(데드락 포함)은 어떤 시나리오에서도 허용하지 않는다
                assertThat(response.getStatus()).as(body(response)).isIn(200, 409);
                responses.add(response);
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Integer> statuses(List<MockHttpServletResponse> responses) {
        return responses.stream().map(MockHttpServletResponse::getStatus).toList();
    }

    private String body(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
