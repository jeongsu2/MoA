# 작업 현황 (새 Claude가 이어받을 때 먼저 읽기)

> 2026-11-16 기준. 상태가 바뀌면 이 파일을 고친다.

## 지금 어디까지
- Phase 0~4 끝 (기획, 리서치, ERD v2 승인, 면접 질문, 하네스). 지금은 **Phase 5 단계별 구현**.
- 백엔드 기능 6개 모두 develop에 머지됨, 테스트 139개 통과. 화면(프론트)은 아직 없음.
- 기술 선택 이유는 ADR-004~009 (`docs/decisions/`).
- 레포는 moamarket 조직 아래 3개 (D-41): Backend(이 레포, API와 문서, 하네스), [Frontend](https://github.com/moamarket/Frontend), [Infra](https://github.com/moamarket/Infra). 로컬은 `~/Projects/MoA`, `~/Projects/MoA-Frontend`, `~/Projects/MoA-Infra`.

| 이슈 | 기능 | PR | 기능 문서 | 학습 포인트 |
|---|---|---|---|---|
| #26 | 회원가입과 로그인 | #32 | features/01-auth.md | learning/01-auth.md |
| #27 | My 배송지 관리 | #37 | features/02-address.md | learning/02-address.md |
| #28 | 판매 글과 상품 등록 | #38 | features/03-post.md | learning/03-post.md |
| #29 | 댓글 주문과 선착순 당첨 | #39 | features/04-order-claim.md | learning/04-order-claim.md |
| #30 | 입금완료 신고와 입금확인 | #40 | features/05-deposit.md | learning/05-deposit.md |
| #31 | 5분 만료와 차순위 승계 | #41 | features/06-expiry.md | learning/06-expiry.md |

- 3~6번 기능의 열린 질문은 모두 확정됨 (D-37~D-40).

## 정수 확인을 기다리는 것
- D-36(JPA ddl-auto로 시작, 배포 전 Flyway)은 Claude 기본값.
- ADR-006 세부 기준 (모든 상태 전이가 product 먼저, 락 순서, READ COMMITTED)과 ADR-007 세부값 (한 번에 100개, claim마다 트랜잭션, 앱 Clock, `<=` 경계)은 Claude 기본값.

## 꼭 알아야 할 구현 기준
- 당첨, 입금, 만료의 락 순서, 시계, 5분 경계, READ COMMITTED 이유: `docs/features/claim-implementation-notes.md`
- 테스트는 Testcontainers MySQL (Docker 필요). 테스트 커밋 type은 `chore` (hook이 `test`를 막음).
- 되돌리기 어려운 기술 결정은 ADR을 쓰고 그 기능 PR에 같이 넣는다 (CLAUDE.md).

## 로컬 실행
- 정수 맥: `~/Projects/MoA`. 앱 실행은 레포 루트에서 `./gradlew bootRun` (Docker Desktop 켜고).
- 로컬 MySQL은 compose로 **3307** 포트 (정수 맥에 다른 MySQL이 3306을 씀). 접속 `localhost:3307`, `moa` / `moa`.

## 다음 할 일
1. 프론트 프레임워크 결정 (옵션 2~3개 비교 후 정수 결정), 목데이터로 화면 구현 (D-42, frontend-dev, Frontend 레포 prototype/ 흐름 기준)
2. 나중에: API 연결, 부하 테스트(k6)로 폴링과 락 한계 측정
3. 배포 전: 도서산간 우편번호 목록 채우기 (`src/main/resources/remote-area-zip-codes.csv`, 지금은 제주, 울릉만), Flyway(D-36), S3(ADR-009)
