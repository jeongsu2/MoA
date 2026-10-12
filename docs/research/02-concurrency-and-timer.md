# Phase 1-2. 선착순 동시성과 5분 타이머 리서치

> 2026-10-05, 대상: MoA, 백엔드: Java + Spring Boot (ADR-002)
> 이 문서는 옵션과 트레이드오프만 정리한다. 결정은 ERD 단계에서 정수님이 한다.

## 0. 문제를 정확히 쓰면

MoA에서 동시성 문제가 생기는 곳은 세 군데다.

1. **당첨 판정:** 상품 하나(수량 1)에 정확한 댓글이 동시에 여러 개 들어올 때, 정확히 한 명만 ⭐가 붙어야 한다.
2. **1인당 5개 제한 (D-13):** 한 사람이 같은 판매 글의 여러 상품에 동시에 댓글을 달면, 각 요청이 "아직 4개니까 괜찮다"고 보고 동시에 통과해 6개가 될 수 있다.
3. **5분 만료와 차순위 승계 (D-04, D-07):** 5분이 지나면 정확히 한 번만 취소되고, 다음 댓글로 정확히 한 번만 넘어가야 한다. 구매자의 "입금완료" 신고와 만료 처리가 동시에 일어날 수도 있다.

공통 원인은 **"읽고 확인한 다음 쓰기"(check-then-act)** 사이에 다른 요청이 끼어드는 경쟁 조건(race condition)이다.
- 면접 질문 예시: "경쟁 조건이 무엇이고, 이 서비스에서는 어디서 생기나요?"

### "먼저"의 기준이 생각보다 까다롭다
댓글 ID(DB 자동 증가 번호)를 순서로 쓰면 될 것 같지만, MySQL은 ID를 **INSERT할 때** 주고 다른 요청은 **COMMIT한 순서**대로 보게 된다. 그래서 ID 10번 댓글보다 11번 댓글이 먼저 커밋되면, 11번이 먼저 당첨 처리를 시도할 수 있다. "누가 먼저인가"를 무엇으로 정할지(댓글 ID, 서버 수신 시각, 락을 잡은 순서)를 ERD에서 한 문장으로 정의해야 한다.

## 1. 당첨 판정 방식 옵션

| 옵션 | 방식 | 장점 | 단점 |
|---|---|---|---|
| **A. DB 비관적 락** | 댓글 저장 트랜잭션에서 상품 행을 `SELECT ... FOR UPDATE`로 잠그고, 판매중이면 당첨 처리. JPA `@Lock(PESSIMISTIC_WRITE)` | 이해하기 쉽고 정합성이 확실함. 락 잡은 순서 = 당첨 순서라 "먼저"의 정의가 명확 | 인기 상품 한 행에 요청이 몰리면 대기열이 생김 (커넥션 풀 고갈 위험) |
| **B. 조건부 UPDATE** | `UPDATE product SET status='RESERVED', winner_id=? WHERE id=? AND status='ON_SALE'` 실행 후 바뀐 행 수가 1이면 당첨 | 락을 오래 잡지 않아 빠름. 한 문장이라 원자적 | 순서는 커밋 순서라 ID 순서와 어긋날 수 있음. 실패한 요청을 어떻게 차순위로 둘지 따로 설계 필요 |
| **C. JPA 낙관적 락** | 엔티티에 `@Version`, 충돌 시 예외 후 재시도 | 충돌이 드물면 가장 가벼움 | 선착순처럼 **충돌이 몰리는 곳에는 부적합**. 재시도가 폭증함 |
| **D. Redis 원자 연산** | `SET product:{id}:winner {commentId} NX` 또는 Lua 스크립트로 판정, DB에는 나중에 반영 | 매우 빠르고 DB 부하가 적음 | Redis와 DB 내용이 어긋날 수 있음. Redis 장애 시 판정 불가. 운영 복잡도 증가 |
| **E. 큐로 줄 세우기** | 판매 글마다 댓글을 Redis Stream/Kafka에 넣고 한 소비자가 순서대로 처리 | 순서가 완벽하게 보장되고 DB 경합이 없음 | ⭐가 바로 안 붙고 약간 늦게 붙음. 인프라가 가장 복잡 |

**참고:** 분산 락(Redisson 등)은 위 방식들을 "감싸는" 도구다. Kleppmann은 락의 목적을 **효율**(중복 작업 줄이기)과 **정합성**(데이터가 틀리면 안 됨)으로 나누고, 정합성이 목적이면 Redis 락만 믿지 말고 DB 쪽에서도 막으라고 권한다(펜싱 토큰). MoA의 당첨은 틀리면 안 되는 **정합성** 문제라, 어떤 옵션이든 마지막 방어선은 DB 조건(상태 체크, 유니크 제약)에 두는 게 안전하다.

- 면접 질문 예시: "비관적 락과 낙관적 락 중 선착순에는 무엇을 쓰고, 이유는?"
- 면접 질문 예시: "Redis 분산 락이 있는데 왜 DB에서도 한 번 더 막나요?"

### 학습 관점 제안 (결정은 아님)
많은 학습 프로젝트가 **A로 먼저 만들고 → 부하 테스트(k6, nGrinder)로 한계를 측정하고 → D나 E로 개선**하는 순서를 밟는다. 숫자로 "왜 바꿨는지"를 말할 수 있어 면접에서 가장 설득력 있는 이야기가 된다.

## 2. 1인당 5개 제한 옵션

| 옵션 | 방식 | 장점 | 단점 |
|---|---|---|---|
| A. 사용자 카운터 행 잠금 | (판매 글, 구매자)별 카운터 행을 `FOR UPDATE`로 잠그고 5 미만일 때만 +1 | DB만으로 정확함 | 판정할 때 상품 락과 카운터 락 두 개를 잡음 → **잠그는 순서를 고정**하지 않으면 데드락 |
| B. 조건부 UPDATE 카운터 | `UPDATE quota SET cnt=cnt+1 WHERE post_id=? AND user_id=? AND cnt<5` | 한 문장으로 원자적, 락 시간 짧음 | 당첨 실패 시 카운트를 되돌리는 처리 필요 |
| C. Redis INCR | `INCR quota:{postId}:{userId}` 후 5 초과면 DECR | 빠름 | 1번의 D와 같은 Redis/DB 불일치 문제 |

- 면접 질문 예시: "데드락이 무엇이고, 락 두 개를 잡을 때 어떻게 예방하나요?"

## 3. 5분 만료와 차순위 승계 옵션

| 옵션 | 방식 | 장점 | 단점 |
|---|---|---|---|
| **A. DB 폴링 스케줄러** | `deadline` 컬럼 저장, `@Scheduled`로 몇 초마다 `deadline < now`인 당첨을 찾아 취소하고 승계 | 서버가 꺼져도 DB에 남아 있어 **유실 없음**. 이해하기 쉬움 | 폴링 간격만큼 늦게 처리됨. 서버가 여러 대면 중복 실행 방지 필요 (ShedLock, `FOR UPDATE SKIP LOCKED`) |
| **B. Redis TTL + 만료 알림** | 당첨 키에 TTL 5분, keyspace notification으로 만료 이벤트 수신 | 구현이 짧음 | Redis 공식 문서: 알림은 **"fire and forget"**이라 구독 서버가 끊겨 있으면 이벤트가 사라지고, 만료 이벤트가 **TTL이 0이 된 시점이 아니라 실제 삭제 시점**에 나와 지연될 수 있음. 단독으로 쓰기엔 위험 |
| **C. 지연 큐** | Redis Sorted Set(점수 = 만료 시각)이나 Redisson `RDelayedQueue`, RabbitMQ 지연 메시지 | 정시에 가깝게 처리, 처리량 높음 | 큐 인프라 추가. 큐와 DB가 어긋날 때의 복구 설계 필요 |
| **D. 지연 평가 (lazy)** | 타이머 없이, 누군가 조회하거나 다음 행동을 할 때 `now > deadline`이면 만료로 보고 그때 승계 | 타이머가 아예 없어 가장 단순 | 아무도 안 보면 상태가 안 바뀜 → 보통 A와 함께 씀 |

### 꼭 지켜야 할 것: 정확히 한 번
어떤 옵션이든 상태 변경은 **조건부 UPDATE로 상태 전이를 지킨다**. 예: `UPDATE reservation SET status='EXPIRED' WHERE id=? AND status='WON' AND deadline < now`. 이러면 스케줄러가 두 번 돌거나, 구매자가 4분 59초에 "입금완료"를 누르는 동시에 만료가 실행돼도 **먼저 커밋된 쪽 하나만** 성공한다. 이것이 멱등성과 상태 머신 설계의 핵심이다.

- 면접 질문 예시: "5분 타이머가 도는 중에 서버가 재시작되면 어떻게 되나요?"
- 면접 질문 예시: "Redis 만료 이벤트만으로 결제 만료를 처리하면 왜 위험한가요?"
- 면접 질문 예시: "`SKIP LOCKED`는 무엇이고 언제 쓰나요?" (MySQL 8.0 문서: 잠긴 행을 건너뛰어 일관되지 않은 결과를 주므로 일반 트랜잭션엔 부적합하고 큐 같은 테이블에 적합)

## 4. ERD에서 정해야 할 것 (다음 단계 질문)
1. "먼저"의 기준: 댓글 ID, 서버 수신 시각, 락 획득 순서 중 무엇?
2. 당첨 판정 방식: 1장 A~E 중 무엇으로 시작할지
3. 5분 만료 방식: 3장 A~D 중 무엇 (또는 A+D 조합)
4. Redis를 처음부터 넣을지, DB만으로 시작해 측정 후 넣을지

## 5. 핵심 키워드와 추천 자료
**키워드:** 경쟁 조건, 트랜잭션 격리 수준, 비관적 락/낙관적 락, `SELECT FOR UPDATE`, 데드락, 분산 락, 펜싱 토큰, 멱등성, 상태 머신, 지연 큐

- 입문: [Redis로 분산 락을 구현해 동시성 이슈를 해결해보자 (hudi.blog)](https://hudi.blog/distributed-lock-with-redis/): SETNX 스핀락과 Redisson 차이, 락 해제와 트랜잭션 커밋 순서 문제
- 실전: [How to do distributed locking (Martin Kleppmann)](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html): 효율 vs 정합성, 펜싱 토큰, Redlock 비판
- 공식 문서: [MySQL 8.0 Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html), [Redis keyspace notifications](https://redis.io/docs/latest/develop/pubsub/keyspace-notifications/)
- 사례: [선착순 쿠폰 Redis Lua + Kafka 예제](https://github.com/SongHyeonJin/fast-coupon-service), [MySQL 단독 동시성 한계 실측 이슈](https://github.com/f-lab-edu/givemeticon/issues/154), [에스코어: Redis로 안전하게 동시성 제어하기](https://s-core.co.kr/insight/view/redis%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%95%88%EC%A0%84%ED%95%98%EA%B2%8C-%EB%8F%99%EC%8B%9C%EC%84%B1-%EC%9D%B4%EC%8A%88-%EC%A0%9C%EC%96%B4%ED%95%98%EA%B8%B0/)
- 책: 입문 『Real MySQL 8.0 1권』(잠금과 트랜잭션 장), 실전 『데이터 중심 애플리케이션 설계』(7장 트랜잭션, 8장 분산 시스템의 문제)
