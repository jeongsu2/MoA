# 기능 04. 댓글 주문과 선착순 당첨

> 2026-10-13, 작성: pm, 상태: **진행 (2026-10-13, 열린 질문은 추천안으로 구현, 정수 확인 전)**, 이슈 #29
> 입력: [requirements.md](../requirements.md) F-04, F-04a, F-05, F-05a, D-11, D-13, D-18, D-20, D-22 / [erd-v2.md](../erd/erd-v2.md) `comment`, `claim`, `product`, `purchase_quota`, 3장, 4장, 5장 / [02 동시성](../research/02-concurrency-and-timer.md) 1장, 2장 / [03b 실시간](../research/03b-realtime-detail.md)

## 1. 유저 스토리
- US-1. 구매자로서, 판매 오픈 순간 "번호 + 제품명"을 댓글로 달고, 내가 당첨됐는지 바로 알고 싶다 (F-04, F-05).
- US-2. 구매자로서, 수백 명이 동시에 댓글을 달아도 서버가 공정하게 한 명만 당첨시키길 바란다.
- US-3. 판매자로서, 당첨 댓글에서 당첨자 실명을 바로 보고 통장 입금자명과 대조하고 싶다 (F-05, D-05).
- US-4. 글을 보는 사람으로서, 새 댓글과 당첨 표시가 새로고침 없이 몇 초 안에 바뀌길 바란다 (D-18).

## 2. 수용 기준 (Given / When / Then)

### 댓글 작성
- AC-1. Given 로그인한 ACTIVE 사용자, OPEN 글, When 댓글을 쓰면, Then `comment`가 생기고 201. 비로그인은 401, DRAFT 글은 404. 판매자도 다른 판매자 글에 주문할 수 있다 (D-34).
- AC-2. Given 댓글 수정 API, Then 없다 (D-11). 잘못 썼으면 새 댓글을 달고, 순서는 새 댓글 기준이다 (F-04a).
- AC-3. Given 빈 댓글이나 길이 초과, When 쓰면, Then 400.

### 주문 매칭
- AC-4. Given 댓글 내용이 그 글의 상품 하나와 정확히 맞음 (판정 규칙은 Q-1), When 저장하면, Then `type=ORDER`, `product_id`=그 상품.
- AC-5. Given 어떤 상품과도 맞지 않음, When 저장하면, Then `type=NORMAL`, `product_id`=null. 에러가 아니라 일반 댓글이다.
- AC-6. Given 상품이 RESERVED나 SOLD, When 정확한 댓글을 쓰면, Then ORDER로 저장되고 당첨은 안 된다. 이 댓글은 차순위 후보가 된다 (06 문서).

### 당첨 판정 (동시성)
- AC-7. Given 상품이 ON_SALE, When 정확한 댓글이 저장되면, Then 같은 트랜잭션에서 `product` 행을 `SELECT ... FOR UPDATE`로 잡고, 아직 ON_SALE이면 `claim`(status=WON, `won_at`=서버 시각, `deadline_at`=`won_at`+5분)을 만들고 `product.status=RESERVED`, `current_claim_id`=새 claim (ERD 4장).
- AC-8. Given 같은 상품에 정확한 댓글 N개가 동시에 도착, When 모두 처리되면, Then claim은 정확히 1개, 나머지 N-1개는 ORDER 댓글로만 남는다. 테스트: 스레드 100개 동시 요청 후 WON 1개 확인 (Testcontainers MySQL, D-33).
- AC-9. Given 거의 동시에 온 댓글, Then 당첨은 **락을 먼저 잡은 쪽**이다. 화면상 앞 댓글(작은 id)이 아닌 뒤 댓글이 당첨될 수 있고, 이는 정한 동작이다 (D-22).
- AC-10. Given 판정 시각, Then 클라이언트 시각은 쓰지 않고 서버 시각만 쓴다 (5장 공정성).

### 1인 5개 제한 (D-13)
- AC-11. Given 한 글에서 내 `purchase_quota.active_count`(당첨 중 + 확정)가 5, When 6번째 상품에 정확한 댓글을 쓰면, Then ORDER로 저장되지만 당첨은 건너뛰고 상품은 ON_SALE 그대로다. 다음 사람이 당첨될 수 있다.
- AC-12. Given 5개 미만, When 당첨되면, Then 조건부 UPDATE `active_count < 5`로 +1 하고, 바뀐 행이 0이면 당첨하지 않는다. 행이 없으면 먼저 만든다.
- AC-13. Given 한 사람이 같은 글의 상품 6개에 동시에 댓글, When 모두 처리되면, Then 당첨은 최대 5개다.
- AC-14. Given 락을 두 개 잡는 판정, Then 순서는 항상 **product 먼저, quota 나중** (데드락 예방, ERD 4장).

### 피드 (짧은 폴링, D-18)
- AC-15. Given 글을 보는 누구나, When 피드 API를 부르면, Then 새 댓글(`afterId` 이후만), 모든 상품의 현재 상태와 진행 중 당첨(댓글 id, 당첨자 닉네임, 상태, `deadlineAt`), `serverTime`을 한 번에 받는다.
- AC-16. Given 프론트, Then 2초마다 피드를 부른다 (D-18 계산 가정값). 화면이 다시 보이면(`visibilitychange`) 바로 한 번 부른다 (03b 7장).
- AC-17. Given 당첨 표시, Then 댓글 목록이 아니라 상품의 진행 중 당첨으로 그린다. 그래야 예전 댓글의 당첨이 만료, 승계로 바뀌어도 `afterId` 폴링으로 반영된다.
- AC-18. Given 그 글의 판매자, When 피드를 받으면, Then 진행 중 당첨에 당첨자 **실명**이 들어 있다. 다른 사람에게는 실명 필드 자체가 없다 (ERD 5장).
- AC-19. Given 처음 진입이나 댓글이 많은 글, When `afterId`=0으로 부르면, Then 한 번에 최대 100개, `hasMore`로 이어 받는다.
- AC-20. Given 남은 시간, Then 서버는 `deadlineAt`과 `serverTime`만 주고 카운트다운은 브라우저가 한다 (03 리서치 3장).

## 3. 제안 API
| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/posts/{postId}/comments` | content | 201, commentId, type(NORMAL, ORDER), productId, won(true/false), claim{claimId, status, deadlineAt} 또는 null |
| GET | `/api/posts/{postId}/feed?afterId=` | (쿼리) | 200, serverTime, postStatus, products[{productId, itemNo, status, claim{claimId, commentId, buyerNickname, buyerRealName(판매자만), status, deadlineAt} 또는 null}], comments[{commentId, type, parentId, nickname, content, productId, createdAt}], hasMore, myClaims(05 문서) |

- 피드를 하나로 합친 이유: 폴링 한 번에 요청 하나. 댓글, 상품 상태를 따로 부르면 요청이 두 배가 된다 (D-18의 1분 15,000번 가정).
- 피드는 읽기만 한다. 만료, 승계 같은 쓰기는 일으키지 않는다 (06 문서 Q-3과 연결).
- 댓글은 `id > afterId`에 더해 최근 5초(`serverTime - 5초` 이후) 안에 만든 댓글도 겹쳐 준다. 작은 id 댓글이 늦게 커밋되면 id만으로는 놓치기 때문이고, 중복은 프론트가 `commentId`로 거른다.
- `comment (post_id, id)` 인덱스로 "마지막 id 이후만" 읽는다 (ERD 4장).
- 당첨자 본인에게만 보이는 입금 정보(`myClaims`)는 [05-deposit.md](05-deposit.md).

## 4. 범위 밖
- 댓글 삭제 (ERD 5장 규칙 "일반 댓글만, 진행 중 당첨 댓글은 불가"는 그대로 두고 나중에. 폴링에 삭제 반영 처리가 추가로 필요해짐)
- 사용자의 대댓글 (`parent_id`는 지금 SYSTEM 감사 댓글만 씀)
- SSE, WebSocket (D-18, 부하 측정 뒤)
- Redis, 큐로 판정 (02 리서치 1장 D, E. 비관적 락으로 시작해 측정)
- 댓글 도배 방지, 요청 횟수 제한
- 당첨 알림 (푸시, 카카오톡)
- 서버 여러 대 (ShedLock, 세션 공유)

## 5. 열린 질문 (정수 결정 필요)
| ID | 질문 | 옵션 (트레이드오프) | 추천 |
|---|---|---|---|
| Q-1 | "정확히 기재" 판정 규칙 (R-04) | A. 정규화 후 완전 일치: 앞뒤 공백 제거, 번호 뒤 `.` 있어도 없어도 됨, 공백 무시, 영문 대소문자 무시. 그 외 글자가 더 있으면 실패 (원문 "정확히"에 가깝고 규칙이 설명하기 쉬움, "구매할게요"를 붙이면 실패) / B. 번호만 맞으면 인정 (가장 관대, 번호 오타로 다른 상품에 당첨) / C. 상품 버튼을 누르면 댓글이 자동 작성 (오타가 없지만 원문의 타이핑 방식과 다름, 프론트 비용) | A |
| Q-2 | 판매자가 **자기 글**에 정확한 댓글을 쓰면? | A. 주문으로 안 잡고 NORMAL로 저장 (판매자 공지 댓글이 실수로 당첨되지 않음, 조건 하나) / B. 다른 구매자와 똑같이 처리 (규칙 없음, 판매자가 자기 상품을 잡아 버릴 수 있음) | A |

## 6. 관련 결정
F-04, F-04a, F-05, F-05a, D-05, D-11, D-13, D-18, D-20, D-22, D-27(ERD v2), D-33(Testcontainers), D-34(판매자도 구매)

## 7. 면접 질문 예시
"같은 상품에 댓글 100개가 동시에 오면 어떻게 한 명만 당첨되나요? 화면상 첫 댓글이 아닌 사람이 당첨되면 버그인가요?" (답의 방향: 상품 행을 `FOR UPDATE`로 잡아 줄을 세우고, 락을 잡은 순서가 곧 당첨 순서다. id는 INSERT 때, 보이는 순서는 COMMIT 때 정해져 둘이 어긋날 수 있다. 이를 알고 정의로 정했다(D-22). 마지막 방어선은 `current_claim_id` 조건부 UPDATE와 `claim.comment_id` 유니크다)
