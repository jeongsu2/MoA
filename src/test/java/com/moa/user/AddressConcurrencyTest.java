package com.moa.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 배송지 변경은 users 행 FOR UPDATE로 줄을 선다 (D-34). 동시에 보내도 규칙이 깨지지 않는지 본다
class AddressConcurrencyTest extends IntegrationTestSupport {

    private static final int REPEAT = 10;
    private static final String ADDRESSES = "/api/me/addresses";

    @AfterEach
    void checkOneDefaultPerUser() {
        assertThat(countUsersWithoutExactlyOneDefault()).isZero();
    }

    @Test
    @DisplayName("S-03 서로 다른 두 배송지를 동시에 기본으로: 둘 다 200, 기본은 정확히 1개")
    void concurrentChangeDefault() throws Exception {
        for (int i = 0; i < REPEAT; i++) {
            cleanTables();
            MockHttpSession session = signupBuyer("buyer01", "구매자1");
            Long secondId = addAddress(session, "11111");
            Long thirdId = addAddress(session, "22222");

            List<Integer> statuses = sendAtTheSameTime(
                    withCsrf(put(ADDRESSES + "/" + secondId + "/default")).session(session),
                    withCsrf(put(ADDRESSES + "/" + thirdId + "/default")).session(session));

            assertThat(statuses).containsExactly(200, 200);
            List<Long> defaultIds = jdbcTemplate.queryForList(
                    "select id from address where is_default = true", Long.class);
            assertThat(defaultIds).hasSize(1);
            assertThat(defaultIds.get(0)).isIn(secondId, thirdId);
            assertThat(countUsersWithoutExactlyOneDefault()).isZero();
        }
    }

    @Test
    @DisplayName("C-06 9개일 때 동시에 2개 추가: 201 하나, 400 하나, 최종 10개")
    void concurrentAddAtLimit() throws Exception {
        for (int i = 0; i < REPEAT; i++) {
            cleanTables();
            MockHttpSession session = signupBuyer("buyer01", "구매자1");
            for (int j = 0; j < AddressService.MAX_ADDRESS_COUNT - 2; j++) {
                addAddress(session, "1000" + j);
            }
            assertThat(countRows("address")).isEqualTo(AddressService.MAX_ADDRESS_COUNT - 1);

            List<Integer> statuses = sendAtTheSameTime(
                    addRequest(session, "20000"),
                    addRequest(session, "30000"));

            assertThat(statuses).containsExactlyInAnyOrder(201, 400);
            assertThat(countRows("address")).isEqualTo(AddressService.MAX_ADDRESS_COUNT);
            assertThat(countUsersWithoutExactlyOneDefault()).isZero();
        }
    }

    // CSRF 토큰은 미리 받아 두고, 두 스레드가 준비되면 같이 출발한다
    private List<Integer> sendAtTheSameTime(MockHttpServletRequestBuilder first,
            MockHttpServletRequestBuilder second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> sendWhenStarted(first, ready, start)));
            futures.add(executor.submit(() -> sendWhenStarted(second, ready, start)));
            ready.await();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private int sendWhenStarted(MockHttpServletRequestBuilder request, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder addRequest(MockHttpSession session, String zipCode) throws Exception {
        Map<String, Object> body = addressBody(zipCode);
        return withCsrf(post(ADDRESSES)).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    private Long addAddress(MockHttpSession session, String zipCode) throws Exception {
        String response = mockMvc.perform(addRequest(session, zipCode))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }
}
