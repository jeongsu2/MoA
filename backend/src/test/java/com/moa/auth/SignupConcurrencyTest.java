package com.moa.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.moa.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 앱에서 먼저 조회해 막지 않으므로, 동시에 와도 DB 유니크 예외가 409로 바뀌는지 본다
class SignupConcurrencyTest extends IntegrationTestSupport {

    private static final int REPEAT = 10;

    @Test
    @DisplayName("A-10 같은 아이디로 구매자, 판매자 동시 가입: 201 하나, 409 하나, 500 없음")
    void concurrentSignupWithSameLoginId() throws Exception {
        for (int i = 0; i < REPEAT; i++) {
            cleanTables();
            List<Integer> statuses = signupAtTheSameTime(
                    "/api/auth/signup/buyer", buyerBody("same01", "구매자" + i),
                    "/api/auth/signup/seller", sellerBody("same01", "판매자" + i));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(countRows("users")).isEqualTo(1);
            assertThat(countRows("address")).isEqualTo(1);
            String role = jdbcTemplate.queryForObject("select role from users", String.class);
            assertThat(countRows("seller_profile")).isEqualTo("SELLER".equals(role) ? 1 : 0);
        }
    }

    @Test
    @DisplayName("A-11 같은 닉네임으로 동시 가입: 한 명만 성공, 500 없음")
    void concurrentSignupWithSameNickname() throws Exception {
        for (int i = 0; i < REPEAT; i++) {
            cleanTables();
            List<Integer> statuses = signupAtTheSameTime(
                    "/api/auth/signup/buyer", buyerBody("buyer" + i + "a", "같은닉네임"),
                    "/api/auth/signup/buyer", buyerBody("buyer" + i + "b", "같은닉네임"));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(countRows("users")).isEqualTo(1);
            assertThat(countRows("address")).isEqualTo(1);
        }
    }

    private List<Integer> signupAtTheSameTime(String firstUrl, Map<String, Object> firstBody,
            String secondUrl, Map<String, Object> secondBody) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> signupWhenStarted(firstUrl, firstBody, ready, start)));
            futures.add(executor.submit(() -> signupWhenStarted(secondUrl, secondBody, ready, start)));
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

    // CSRF 토큰은 미리 받아 두고, 두 스레드가 가입 요청만 동시에 보낸다
    private int signupWhenStarted(String url, Map<String, Object> body, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        MockHttpServletRequestBuilder request = jsonPost(url, body);
        ready.countDown();
        start.await();
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }
}
