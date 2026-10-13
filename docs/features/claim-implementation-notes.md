# 당첨 기능(#29~#31) 구현 기준

> 2026-10-13, qa가 찾은 위험에 대한 기술 기본값 (Claude가 정함, 제품 규칙은 바꾸지 않음). #29~#31 구현은 이 기준을 따른다.

1. **시계는 앱 Clock 하나.** DB `NOW()`를 쓰지 않고, `Clock`에서 읽은 시각을 UPDATE 조건에 넘긴다. 테스트는 고정 Clock.
2. **5분 경계:** 입금완료는 `now < deadline_at`일 때만 성공, 만료는 `deadline_at <= now`. 정확히 5분 0초는 만료.
3. **시각 컬럼은 `datetime(6)`.** 반올림 문제를 없앤다.
4. **claim 상태를 바꾸는 모든 트랜잭션은 먼저 product 행을 FOR UPDATE.** 당첨, 입금완료, 입금확인, 입금 안 됨, 만료가 같은 상품에서 한 줄로 선다. 그 뒤에 claim을 조건부 UPDATE (상태 조건 포함, 바뀐 행 수로 승패 판단). 당첨 판정의 실제 순서는 product → quota → 댓글 INSERT → claim이고, 시각은 락을 잡은 뒤에 읽는다.
5. **락 순서:** product → purchase_quota. quota 행을 두 개 잡을 땐 `user_id` 작은 순서.
   - 입금완료(#30)는 product → claim UPDATE → users(구매자 FOR UPDATE) → 기본 배송지 읽기. 배송지 변경(#27)은 users만 잡고 product, claim을 안 건드려서 사이클이 없다. users를 product보다 먼저 잡는 코드는 만들지 않는다.
6. **quota 첫 행:** `INSERT ... ON DUPLICATE KEY UPDATE`로 만든 뒤 잠근다 (조회 후 INSERT 금지).
7. **진 요청의 응답:** 4번 락을 잡은 뒤 다시 읽으므로 앞 요청이 커밋한 결과를 본다. 같은 요청 반복은 200과 현재 상태.
   - 입금 동작(신고, 입금확인, 입금 안 됨)은 `READ COMMITTED`로 연다 (#30). 잠글 상품 id를 알려고 락 전에 claim을 읽는데, `REPEATABLE READ`면 그 첫 읽기에서 스냅샷이 잡혀 락 뒤의 일반 SELECT가 앞 요청의 커밋(주문, 상태, 배송지 변경)을 못 본다.
   - 버린 대안: 락 뒤 읽기를 모두 `FOR SHARE`로 바꾸기 (읽기마다 잊으면 깨짐), claim을 먼저 잠그기 (product → claim 순서와 반대라 데드락).
   - 장애 시나리오: 이 설정을 빼면 신고 연타의 진 요청이 WON을 보고 409를 낸다. `REPEATABLE READ`로 돌려 DepositConcurrencyTest가 실패하는 것을 확인함.
   - 면접 질문 예시: "SELECT ... FOR UPDATE로 락을 잡았는데 그 뒤 일반 SELECT가 옛 값을 보는 이유는?"
8. **테스트:** `moa.scheduler.enabled=false`로 테스트에서 스케줄러를 끄고 테스트가 만료 처리를 직접 부른다. 동시성 테스트는 스레드 20개, 10번 반복. Hikari 풀은 테스트에서 30, `innodb_lock_wait_timeout` 기본값(50초).
