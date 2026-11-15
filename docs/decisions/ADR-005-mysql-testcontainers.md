# ADR-005. DB와 테스트: MySQL 8.4 + Testcontainers (H2 안 씀)

- 결정: 2026-10-12 (D-30), 2026-10-13 (D-33), 결정자: 정수, 상태: 확정
- 정리: 2026-11-15 (결정 뒤에 결정 로그로 정리함)
- 관련: [phase5-1-skeleton.md](../setup/phase5-1-skeleton.md), [claim-implementation-notes.md](../features/claim-implementation-notes.md)

## 결정
- DB는 MySQL 8.4(LTS). 로컬은 Docker Compose로 띄운다 (Spring Boot Docker Compose 지원, 포트 3307).
- 테스트도 Testcontainers로 같은 버전의 진짜 MySQL을 띄워서 돌린다.
- `open-in-view: false`.

## 이유
- MoA의 핵심은 선착순 동시성이고, 그 정합성을 `SELECT ... FOR UPDATE`, 조건부 UPDATE, 격리 수준 같은 **DB 동작**으로 지킨다 ([ADR-006](ADR-006-claim-lock.md)). 테스트 DB가 다르면 이 동작을 검증할 수 없다.
- H2는 MySQL과 락 동작이 다르다 (D-33). 실제로 격리 수준을 REPEATABLE READ로 돌리면 실패하는 동시성 테스트(DepositConcurrencyTest)는 진짜 MySQL에서만 의미가 있다.
- 로컬과 테스트의 DB 버전이 같아서 "로컬에선 되는데 테스트에선 안 됨"이 없다. PC에 MySQL을 설치하지 않아도 된다.
- `open-in-view`를 끄면 요청이 끝날 때까지 DB 커넥션을 잡지 않는다. 판매 오픈 때 커넥션 풀이 마르는 것을 줄인다.

## 버린 대안
- **H2 인메모리로 테스트:** 빠르고 Docker가 필요 없지만, MySQL 락 동작이 달라 동시성 테스트에 맞지 않는다.
- **PC에 MySQL 직접 설치:** 버전이 사람마다 달라지고, 정수 맥에는 이미 다른 MySQL이 3306을 쓴다.

## 장애 시나리오 / 리스크
- Docker Desktop이 꺼져 있으면 앱도 테스트도 안 뜬다.
- 테스트가 컨테이너를 띄우는 만큼 느리다 (139개 기준 감수).

## 다시 볼 조건
- 테스트 시간이 개발을 막을 만큼 길어질 때 (컨테이너 재사용 설정 검토).

## 면접 질문 예시
"테스트를 H2로 돌리면 안 되는 이유는 무엇이고, 동시성 테스트가 진짜로 동시성을 검증한다는 건 어떻게 확인했나요?"
