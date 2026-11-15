# 당첨 기능(#29~#31) 구현 기준

> 2026-10-13, qa가 찾은 위험에 대한 기술 기본값 (Claude가 정함, 제품 규칙은 바꾸지 않음). #29~#31 구현은 이 기준을 따른다.

1. **시계는 앱 Clock 하나.** DB `NOW()`를 쓰지 않고, `Clock`에서 읽은 시각을 UPDATE 조건에 넘긴다. 테스트는 고정 Clock.
2. **5분 경계:** 입금완료는 `now < deadline_at`일 때만 성공, 만료는 `deadline_at <= now`. 정확히 5분 0초는 만료.
3. **시각 컬럼은 `datetime(6)`.** 반올림 문제를 없앤다.
4. **claim 상태를 바꾸는 모든 트랜잭션은 먼저 product 행을 FOR UPDATE.** 당첨, 입금완료, 입금확인, 입금 안 됨, 만료가 같은 상품에서 한 줄로 선다. 그 뒤에 claim을 조건부 UPDATE (상태 조건 포함, 바뀐 행 수로 승패 판단). 당첨 판정의 실제 순서는 product → quota → 댓글 INSERT → claim이고, 시각은 락을 잡은 뒤에 읽는다.
5. **락 순서:** product → purchase_quota. quota 행을 두 개 잡을 땐 `user_id` 작은 순서.
6. **quota 첫 행:** `INSERT ... ON DUPLICATE KEY UPDATE`로 만든 뒤 잠근다 (조회 후 INSERT 금지).
7. **진 요청의 응답:** 4번 락을 잡은 뒤 다시 읽으므로 앞 요청이 커밋한 결과를 본다. 같은 요청 반복은 200과 현재 상태.
8. **테스트:** `moa.scheduler.enabled=false`로 테스트에서 스케줄러를 끄고 테스트가 만료 처리를 직접 부른다. 동시성 테스트는 스레드 20개, 10번 반복. Hikari 풀은 테스트에서 30, `innodb_lock_wait_timeout` 기본값(50초).
