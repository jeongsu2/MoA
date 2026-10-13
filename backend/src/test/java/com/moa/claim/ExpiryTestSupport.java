package com.moa.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.comment.ClaimTestSupport;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

// 만료와 승계 테스트 공통. 당첨은 FIXED_NOW(10:00:00)라 deadline_at은 10:05:00 (06 테스트 계획 0장).
// 테스트 프로필은 스케줄러 자동 실행을 꺼서 만료는 테스트가 직접 부른다
abstract class ExpiryTestSupport extends ClaimTestSupport {

    protected static final String CLAIMS = "/api/claims";
    protected static final LocalDateTime DEADLINE = FIXED_NOW.plusMinutes(5);

    @Autowired
    protected ClaimExpiryScheduler claimExpiryScheduler;

    @Autowired
    protected ClaimExpiryService claimExpiryService;

    // 정확한 댓글을 쓰고 당첨이면 claim id를 돌려준다. 당첨이 아니면 null
    protected Long claimIdOf(JsonNode comment) {
        JsonNode claim = comment.get("claim");
        return claim == null || claim.isNull() ? null : claim.get("claimId").asLong();
    }

    protected MockHttpServletRequestBuilder claimAction(MockHttpSession session, Long claimId, String action)
            throws Exception {
        return withCsrf(post(CLAIMS + "/" + claimId + "/" + action)).session(session);
    }

    protected void closePost(MockHttpSession seller, Long postId) throws Exception {
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/close")).session(seller))
                .andExpect(status().isOk());
    }

    protected String claimStatus(Long claimId) {
        return jdbcTemplate.queryForObject("select status from claim where id = ?", String.class, claimId);
    }

    protected LocalDateTime claimTime(Long claimId, String column) {
        Timestamp time = jdbcTemplate.queryForObject("select " + column + " from claim where id = ?",
                Timestamp.class, claimId);
        return time == null ? null : time.toLocalDateTime();
    }

    protected Long commentIdOfClaim(Long claimId) {
        return jdbcTemplate.queryForObject("select comment_id from claim where id = ?", Long.class, claimId);
    }

    protected int orderCount(Long claimId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from orders where claim_id = ?", Integer.class,
                claimId);
        return count == null ? 0 : count;
    }

    // 상품의 새 진행 중 당첨이 이 댓글이고, 새 5분(now부터)인지 본다
    protected Long assertHandedOver(Long productId, Long endedClaimId, Long expectedCommentId, LocalDateTime now) {
        Long next = currentClaimId(productId);
        assertThat(next).isNotNull().isNotEqualTo(endedClaimId);
        assertThat(productStatus(productId)).isEqualTo("RESERVED");
        assertThat(claimStatus(next)).isEqualTo("WON");
        assertThat(commentIdOfClaim(next)).isEqualTo(expectedCommentId);
        assertThat(claimTime(next, "won_at")).isEqualTo(now);
        assertThat(claimTime(next, "deadline_at")).isEqualTo(now.plusMinutes(5));
        return next;
    }

    // 모든 작업을 CountDownLatch로 같이 출발시킨다. 작업의 예외(데드락 포함)는 그대로 테스트 실패가 된다
    protected <T> List<T> runAtTheSameTime(List<Callable<T>> tasks)
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
