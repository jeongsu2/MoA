# 기능 06. 5분 만료와 차순위 승계

> 2026-10-13, 작성: pm, 상태: **진행 (2026-10-13, 열린 질문은 추천안으로 구현, 정수 확인 전)**, 이슈 #31
> 입력: [requirements.md](../requirements.md) F-06a, D-04, D-07, D-09, D-20, D-22 / [erd-v2.md](../erd/erd-v2.md) `claim`, `product`, `comment`, `purchase_quota`, 3장, 4장, 5장 / [02 동시성](../research/02-concurrency-and-timer.md) 3장

## 1. 유저 스토리
- US-1. 판매자로서, 당첨자가 5분 안에 입금완료를 안 하면 다음 사람에게 자동으로 넘어가길 바란다. 손으로 다음 사람을 찾지 않는다 (D-04).
- US-2. 차순위 구매자로서, 앞 사람이 시간을 넘기면 내 댓글이 자동으로 당첨되고 새 5분을 받고 싶다 (F-06a).
- US-3. 당첨자로서, 서버 처리가 조금 늦어도 5분 안에 누른 신고는 반드시 인정받고, 5분이 지난 신고는 어떤 경우에도 안 되길 바란다 (D-07).
- US-4. 시간을 넘긴 구매자로서, 같은 상품에 새 댓글로 다시 도전하고 싶다 (D-09).

## 2. 수용 기준 (Given / When / Then)

### 만료
- AC-1. Given WON claim, Then `deadline_at`=`won_at`+5분이 DB에 저장된다. 타이머를 메모리에 들고 있지 않는다.
- AC-2. Given 서버 시각이 `deadline_at`을 지난 WON claim, When 만료 스케줄러(`@Scheduled`)가 돌면, Then 조건부 UPDATE `status='EXPIRED', ended_at=now WHERE id=? AND status='WON' AND deadline_at <= now`. 바뀐 행이 1일 때만 아래 승계를 한다 (ERD 3장).
- AC-3. Given 만료된 claim의 구매자, Then 그 글의 `active_count` -1 (D-13, 당첨 중이 아니게 됨).
- AC-4. Given 스케줄러가 겹쳐 두 번 돌거나 같은 claim을 두 스레드가 잡음, When 처리되면, Then 만료와 승계는 한 번뿐이고 새 claim도 1개다. 테스트: 같은 claim에 만료를 동시에 두 번 실행.
- AC-5. Given 구매자가 마감 직전에 [입금완료]를 누르는 순간 만료가 실행됨, Then 두 UPDATE가 모두 `status='WON'` 조건이라 먼저 커밋된 하나만 성공한다. 신고가 이기면 만료는 0행, 만료가 이기면 신고는 409 (05 AC-5).
- AC-6. Given 서버가 꺼져 있는 동안 마감이 지난 claim, When 서버가 다시 뜨면, Then 첫 스케줄러 실행에서 처리된다 (DB에 남아 있어 유실 없음).
- AC-7. Given 만료를 기다리는 claim 여러 개, Then claim 하나당 트랜잭션 하나로 처리한다. 하나가 실패해도 다른 claim은 처리된다.
- AC-8. Given 시각 비교, Then 저장과 비교에 같은 시계 하나만 쓴다 (DB `NOW()` 또는 앱 시계 중 backend가 하나로 통일).

### 차순위 승계 (만료와 [입금 안 됨] 공통)
- AC-9. Given 만료(EXPIRED) 또는 [입금 안 됨](REJECTED, 05 AC-17), Then 같은 승계 로직 하나를 부른다.
- AC-10. Given 승계 트랜잭션, Then `product` 행을 `FOR UPDATE`로 먼저 잡고, quota는 나중에 잡는다 (ERD 4장 락 순서).
- AC-11. Given 후보, Then 같은 상품의 `type=ORDER` 댓글 중 아직 claim이 없는 것만 후보다. 한 번 당첨된 댓글은 다시 후보가 안 된다 (`claim.comment_id` 유니크, ERD 5장). 후보 순서는 댓글 id 순서이고 어디서부터 볼지는 Q-1.
- AC-12. Given 후보 작성자의 `active_count`가 5, Then 이번에는 건너뛰고 다음 후보를 본다 (D-13).
- AC-13. Given 후보가 있음, Then 새 claim(WON, `won_at`=now, `deadline_at`=now+5분), quota +1, `product.current_claim_id`=새 claim, status RESERVED 그대로.
- AC-14. Given 후보가 없음, Then `product.status=ON_SALE`, `current_claim_id`=null. 이후 첫 정확한 새 댓글이 같은 5분 규칙으로 당첨된다 (D-09, 04 AC-7).
- AC-15. Given 시간을 넘긴 사람, When 같은 상품에 새 댓글을 쓰면, Then 다른 사람과 같은 규칙으로 후보가 된다 (D-09). 예전 댓글은 후보가 아니다.
- AC-16. Given 언제나, Then 한 상품의 진행 중 claim(WON, REPORTED)은 최대 1개다. `current_claim_id`가 지금 끝난 claim일 때만 바꾸는 조건부 UPDATE로 지킨다 (D-20).
- AC-17. Given CLOSED 글 (03 문서 Q-2에서 A를 고를 때), Then 승계하지 않고 상품은 ON_SALE로 둔다.

### 화면 반영
- AC-18. Given 만료나 승계, Then 다음 피드 폴링(04 문서)에서 상품의 진행 중 당첨이 바뀐 것으로 보인다. 새로 당첨된 사람은 `myClaims`에서 새 `deadlineAt`과 입금 정보를 받는다.
- AC-19. Given 마감이 지났지만 아직 스케줄러가 안 돈 순간, Then 피드는 WON과 지난 `deadlineAt`을 그대로 준다. 프론트는 "시간 종료, 처리 중"으로 보여주고 [입금완료]를 숨긴다.

## 3. 제안 API
새 외부 API는 없다. 결과는 기존 응답으로 보인다.

| 어디서 | 무엇이 보이나 |
|---|---|
| `GET /api/posts/{postId}/feed` (04 문서) | 상품 상태, 진행 중 당첨(새 당첨자, `deadlineAt`), 새 당첨자의 `myClaims` |
| `POST /api/claims/{claimId}/reject` (05 문서) | `nextClaim` (승계된 claim 또는 null) |

내부 구성 제안 (이름은 backend가 정함):
- `ClaimExpiryScheduler`: 몇 초마다 `claim (status, deadline_at)` 인덱스로 `status='WON' AND deadline_at <= now`인 claim id를 찾아 하나씩 만료 처리.
- `SuccessionService.succeed(productId, endedClaimId)`: 만료와 [입금 안 됨]이 함께 쓰는 승계.
- 차순위 찾기는 `comment (product_id, type, id)` 인덱스를 쓴다 (ERD 4장).

## 4. 범위 밖
- Redis TTL 만료 알림, 지연 큐 (02 리서치 3장 B, C. DB 폴링으로 시작)
- 서버 여러 대의 스케줄러 중복 실행 방지 (ShedLock, `SKIP LOCKED`, 서버 1대로 시작, ERD 4장)
- 만료, 승계 안내 SYSTEM 댓글 ("입금 시간이 지나 다음 분께 넘어갔습니다")
- 승계 당첨자에게 알림 (푸시, 카카오톡)
- 반복 미입금자 차단 (D-09 장애 시나리오로 받아들임)
- 판매자가 마감 시간(5분)을 바꾸는 기능

## 5. 열린 질문 (정수 결정 필요)
| ID | 질문 | 옵션 (트레이드오프) | 추천 |
|---|---|---|---|
| Q-1 | 차순위를 어디서부터 찾나? (D-22로 락 순서와 id 순서가 어긋날 때 갈림) | A. 아직 당첨된 적 없는 정확한 댓글 중 id가 가장 작은 것 (D-22로 순서를 뺏긴 앞 댓글도 기회를 받음, 화면상 "다음 사람"이 아니라 앞 사람이 될 수 있음) / B. 방금 끝난 당첨 댓글보다 id가 큰 것 중 가장 작은 것 (화면상 "다음 댓글"과 같음, 순서를 뺏긴 앞 댓글은 영영 기회가 없음) | A |
| Q-2 | 시간을 넘긴 사람이 **만료 전에** 미리 써 둔 같은 상품 댓글도 후보인가? (예: 같은 댓글을 두 번 단 경우) | A. 후보로 둠 (규칙 그대로, 같은 사람이 바로 다시 5분을 잡을 수 있음, D-09 장애 시나리오로 받아들인 위험) / B. 끝난 claim의 구매자가 만료 시각 전에 쓴 댓글은 건너뜀 ("재도전은 새 댓글"이라는 뜻에 더 맞음, 조건 하나 추가) | A |
| Q-3 | 스케줄러 간격과 "조회 시 지연 평가"(ERD 4장)의 뜻 | A. 판정은 UPDATE 조건의 시각 비교로 언제나 정확하게, 실제 만료와 승계는 스케줄러만, 1초 간격 (피드 GET이 쓰기를 안 해 폴링 폭증 때 안전, 승계가 최대 1초 늦음) / B. 스케줄러 5초 + 피드 조회 때 지난 마감을 보면 그 자리에서 승계 (승계가 빠름, 폴링 GET마다 쓰기와 product 락이 생겨 판매 오픈 때 위험) | A |

## 6. 관련 결정
F-06a, D-04, D-07, D-09, D-13, D-20, D-22, D-27(ERD v2)

## 7. 면접 질문 예시
"구매자가 4분 59초에 입금완료를 누르는 바로 그 순간 만료 스케줄러가 돌면 어떻게 되나요? 서버가 재시작되면요?" (답의 방향: 신고와 만료 둘 다 `WHERE status='WON'` 조건부 UPDATE라 먼저 커밋한 하나만 바뀐다. 진 쪽은 0행이라 아무것도 안 한다. 마감 시각은 DB에 있어 재시작해도 다음 실행에서 처리된다. 메모리 타이머나 Redis 만료 이벤트만 쓰면 이 둘이 깨진다)
