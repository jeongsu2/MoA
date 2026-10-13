# 기능 05. 입금완료 신고, 판매자 입금확인, 입금 안 됨

> 2026-10-13, 작성: pm, 상태: **진행 (2026-10-13, 열린 질문은 추천안으로 구현, 정수 확인 전)**, 이슈 #30
> 입력: [requirements.md](../requirements.md) F-06, F-06b, F-07, D-03, D-06, D-07, D-08, D-24, D-25, D-26, D-34 / [erd-v2.md](../erd/erd-v2.md) `claim`, `orders`, `seller_profile`, `purchase_quota`, 3장, 5장 / [02-address.md](02-address.md) AC-11

## 1. 유저 스토리
- US-1. 당첨자로서, 당첨되면 계좌, 상품 가격, 배송비, 남은 시간을 바로 보고 5분 안에 입금하고 싶다 (D-24).
- US-2. 당첨자로서, 입금 후 [입금완료]를 한 번 누르면 신고가 끝나길 바란다. 배송지는 My의 기본 배송지로 들어간다 (D-24).
- US-3. 판매자로서, 신고된 당첨자의 실명과 입금액을 통장과 대조한 뒤 [입금확인]을 누르면 감사 댓글이 자동으로 달리길 바란다 (F-06b, D-08).
- US-4. 판매자로서, 신고만 하고 입금이 없으면 [입금 안 됨]으로 바로 다음 사람에게 넘기고 싶다 (ERD 5장).
- US-5. 판매자로서, 확정된 주문의 받는 사람, 연락처, 주소를 한 목록으로 보고 일괄 발송하고 싶다.

## 2. 수용 기준 (Given / When / Then)

### 입금 정보 (당첨자에게만)
- AC-1. Given 나의 WON claim, When 피드를 받으면, Then `myClaims`에 판매자 계좌(은행, 계좌번호, 예금주), 상품 가격, 배송비 3,500원, 제주 산간 6,000원, `deadlineAt`이 들어 있다. 당첨자가 아닌 사람에게는 계좌가 안 나간다.
- AC-2. Given 입금 정보 화면, Then "*배송비 3,500원 별도입니다. 제주 산간 지역의 경우 6,000원입니다." 문구(D-25)와 [입금완료] 버튼 위 고정 안내 "배송지는 My의 기본 배송지로 설정됩니다. 배송지 변경을 원하실 경우, 입금완료 전에 수정해주세요!"(D-24)가 보인다.
- AC-3. Given 당첨자, Then 배송지를 고르는 화면은 없다. 다른 주소는 입금완료 전에 My에서 기본을 바꾼다 (D-24, 02 문서).

### 입금완료 신고 (D-06, D-07)
- AC-4. Given 나의 WON claim이고 서버 시각이 `deadline_at` 전, When [입금완료]를 누르면, Then **한 트랜잭션**으로:
  1. 조건부 UPDATE `status='REPORTED', reported_at=now WHERE id=? AND buyer_id=나 AND status='WON' AND deadline_at > now`
  2. 바뀐 행이 1일 때만 `orders` 생성: 지금 기본 배송지를 복사(스냅샷), `price`=상품 가격, `shipping_fee`=산간이면 `remote_shipping_fee` 아니면 `shipping_fee`(글에 저장된 값), `total_amount`=가격 + 배송비 (D-25, D-26)
  3. `DEPOSIT_REPORT` 댓글 "입금완료" 자동 작성 (ERD 5장)
- AC-5. Given `deadline_at`이 지남, When [입금완료]를 누르면, Then 409 "입금 시간이 지났습니다"이고 아무것도 안 생긴다. 만료 스케줄러가 아직 안 돌았어도 같다 (판정은 UPDATE 조건의 시각 비교).
- AC-6. Given 이미 REPORTED인 내 claim, When 다시 누르면 (연타, 두 탭), Then 200과 같은 주문 정보. 주문과 댓글은 하나뿐이다 (`orders.claim_id` 유니크가 최종 방어선).
- AC-7. Given 남의 claim id, When 신고하면, Then 404 (D-34, 남의 것은 404).
- AC-8. Given 입금완료 직전에 My에서 기본 배송지를 바꿈, When 신고하면, Then 주문에는 바꾼 주소가 들어간다. 신고 후 기본을 바꿔도 주문 주소는 그대로다 (D-24, 02 AC-11).
- AC-9. Given 신고 트랜잭션, Then 기본 배송지는 `users` 행 `FOR UPDATE` 아래에서 읽는다. 배송지 변경과 같은 줄에 서서 주소가 반쯤 바뀐 상태를 읽지 않는다 (D-34).
- AC-10. Given 기본 배송지의 우편번호가 산간 목록에 있음, Then 배송비는 6,000원이다. 목록 범위는 Q-1.
- AC-11. Given 신고 성공, Then 응답과 피드 `myClaims`에 `totalAmount`, `shippingFee`, 주소 요약이 보인다.

### 판매자 [입금확인] (F-06b, D-08)
- AC-12. Given 그 글의 판매자, REPORTED claim, When [입금확인]을 누르면, Then 한 트랜잭션으로 조건부 UPDATE `REPORTED`에서 `CONFIRMED`, `confirmed_at`=now, `product.status=SOLD`, 당첨 댓글에 대댓글로 SYSTEM 댓글 "구매감사합니다. 예쁘게 포장해서 전달드릴게요 💓" 작성.
- AC-13. Given 이미 CONFIRMED, When 다시 누르면, Then 200, 감사 댓글은 1개뿐 (D-08 멱등성). 댓글은 UPDATE 바뀐 행이 1일 때만 쓴다.
- AC-14. Given 판매자 확인, Then 시간 제한이 없다 (D-07). REPORTED는 판매자가 누를 때까지 그대로다.
- AC-15. Given WON(아직 신고 전), When [입금확인]을 누르면, Then 409.
- AC-16. Given 그 글의 판매자가 아닌 사람 (다른 판매자, 당첨자 본인 포함), When [입금확인]이나 [입금 안 됨]을 부르면, Then 403. 역할이 아니라 글 작성자와 요청자를 비교한다 (03 리서치 1장). 버튼을 프론트에서 숨기는 것은 보조다.

### 판매자 [입금 안 됨]
- AC-17. Given 그 글의 판매자, REPORTED claim, When [입금 안 됨]을 누르면, Then 조건부 UPDATE `REPORTED`에서 `REJECTED`, `ended_at`=now, 그 구매자 `active_count` -1, 바로 차순위 승계 (06 문서와 같은 승계 로직). `orders` 행은 기록으로 남는다.
- AC-18. Given [입금확인]과 [입금 안 됨]이 동시에 옴, Then 둘 다 `status='REPORTED'` 조건이라 한쪽만 성공하고, 진 쪽은 409.
- AC-19. Given 이미 REJECTED, When 다시 누르면, Then 200, 승계는 한 번뿐.
- AC-20. Given REJECTED된 구매자, Then 새 댓글로 다시 도전할 수 있다 (D-09).

### 판매자 주문 목록
- AC-21. Given 그 글의 판매자, When 주문 목록을 보면, Then REPORTED, CONFIRMED 주문의 상품 번호, 이름, 닉네임, 실명, 입금액, 배송비, 받는 사람, 연락처, 주소, 배송 요청사항, 신고 시각이 온다. 다른 사람은 403.
- AC-22. Given WON(신고 전) 당첨, Then 판매자도 주소, 연락처를 볼 수 없다. 주소는 `orders`가 생긴 뒤에만 공개된다 (ERD 5장).

## 3. 제안 API
| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/claims/{claimId}/report` | 없음 | 200, claimId, status, reportedAt, order{totalAmount, price, shippingFee, zipCode, address1} |
| POST | `/api/claims/{claimId}/confirm` | 없음 | 200, claimId, status, confirmedAt |
| POST | `/api/claims/{claimId}/reject` | 없음 | 200, claimId, status, nextClaim{claimId, commentId, buyerNickname, deadlineAt} 또는 null |
| GET | `/api/posts/{postId}/orders` | 없음 | 200, [claimId, status, itemNo, productName, buyerNickname, buyerRealName, totalAmount, shippingFee, recipientName, recipientPhone, zipCode, address1, address2, deliveryNote, reportedAt, confirmedAt] |
| (피드 확장) | `/api/posts/{postId}/feed` | 04 문서 | `myClaims`[{claimId, productId, status, deadlineAt, bankName, accountNumber, accountHolder, price, shippingFee, remoteShippingFee, order(신고 후만)}] |

- 요청 본문이 비어 있는 이유: 누가 누르는지는 세션, 무엇을 누르는지는 경로로 충분하다. 금액, 주소를 클라이언트에서 받지 않는다 (위조 방지).
- 세 버튼 모두 POST지만 같은 요청을 여러 번 보내도 결과가 같게 만든다 (조건부 UPDATE + 현재 상태 그대로 응답).

## 4. 범위 밖
- PG, 가상계좌, 입금 자동 확인 (D-03, F-07)
- 신고 전 예상 입금액 계산 표시 (문구 안내로 대신, D-25)
- 입금액이 틀렸을 때 차액 처리 (판매자가 [입금확인] 또는 [입금 안 됨]으로 판단)
- 입금완료 신고 취소, [입금확인] 되돌리기
- REPORTED가 오래 묶일 때 자동 처리 (D-07 장애 시나리오로 받아들임, 판매자는 [입금 안 됨] 사용)
- 묶음 배송비 (D-26, 당첨마다 따로)
- 구매자 My 주문 내역 화면
- 주문 목록 엑셀 내보내기, 운송장 (D-02)
- 취소, 거절 때 안내 SYSTEM 댓글

## 5. 열린 질문 (정수 결정 필요)
| ID | 질문 | 옵션 (트레이드오프) | 추천 |
|---|---|---|---|
| Q-1 | 제주 산간 판별 목록을 어디까지 (R-12) | A. 제주만 (우편번호 63으로 시작) (가장 단순, 울릉도 같은 도서산간은 3,500원으로 틀림) / B. 택배사 공개 도서산간 우편번호 목록을 CSV 파일로 넣고 시작할 때 메모리에 올림 (D-25 "제주 산간"을 그대로 지킴, 목록 갱신은 손으로) / C. DB 테이블로 관리 (화면에서 고칠 수 있지만 ERD v3와 관리 기능이 필요, 지금은 과함) | B |

## 6. 관련 결정
F-06, F-06b, F-07, D-03, D-05, D-06, D-07, D-08, D-09, D-24, D-25, D-26, D-27(ERD v2), D-34(남의 것 404, users 행 락)

## 7. 면접 질문 예시
"판매자가 [입금확인]을 빠르게 두 번 누르면 감사 댓글이 두 번 달리지 않나요?" (답의 방향: `UPDATE ... WHERE status='REPORTED'`의 바뀐 행 수가 1일 때만 댓글을 쓴다. 두 번째 요청은 0행이라 아무것도 안 하고 현재 상태만 돌려준다. 앱의 if 검사만으로는 동시 요청에 깨진다)
