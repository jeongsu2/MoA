# 작업 현황 (새 Claude가 이어받을 때 먼저 읽기)

> 2026-10-14 기준. 상태가 바뀌면 이 파일을 고친다.

## 지금 어디까지
- Phase 0~4 끝 (기획, 리서치, ERD v2 승인, 면접 질문, 하네스). 지금은 **Phase 5 단계별 구현**.
- 백엔드 기능 6개 구현 끝, 테스트 139개 통과. 화면(프론트)은 아직 없음.

| 이슈 | 기능 | 브랜치 | 기능 문서 | 학습 포인트 |
|---|---|---|---|---|
| #26 | 회원가입과 로그인 | feat/#26 (PR #32) | features/01-auth.md | learning/01-auth.md |
| #27 | My 배송지 관리 | feat/#27 | features/02-address.md | learning/02-address.md |
| #28 | 판매 글과 상품 등록 | feat/#28 | features/03-post.md | learning/03-post.md |
| #29 | 댓글 주문과 선착순 당첨 | feat/#29 | features/04-order-claim.md | learning/04-order-claim.md |
| #30 | 입금완료 신고와 입금확인 | feat/#30 | features/05-deposit.md | learning/05-deposit.md |
| #31 | 5분 만료와 차순위 승계 | feat/#31 | features/06-expiry.md | learning/06-expiry.md |

- 브랜치는 **앞 브랜치 위에 쌓여 있음** (#27은 #26 위, #28은 #27 위 ...). PR은 #26부터 순서대로 만들고 머지한다. #30과 #31은 이어서 머지 (#30만 있으면 [입금 안 됨] 뒤 승계가 없음).
- 앞 PR이 머지되면 다음 브랜치의 PR 화면에는 그 브랜치 커밋만 남는다. 충돌이 나면 develop을 그 브랜치에 merge해서 푼다.

## 정수 확인을 기다리는 것
- 3~6번 기능 문서의 열린 질문 9개는 **추천안으로 구현함, 정수 확인 전**. 확정되면 requirements.md 8장에 D-37부터 남긴다.
- 도서산간 우편번호 목록(`backend/src/main/resources/remote-area-zip-codes.csv`)은 제주, 울릉만 들어 있음. 택배사 공개 목록으로 채워야 함.
- D-36(JPA ddl-auto로 시작, 배포 전 Flyway)은 Claude 기본값으로 정함.

## 꼭 알아야 할 구현 기준
- 당첨, 입금, 만료의 락 순서, 시계, 5분 경계, READ COMMITTED 이유: `docs/features/claim-implementation-notes.md`
- 테스트는 Testcontainers MySQL (Docker 필요). 테스트 커밋 type은 `chore` (hook이 `test`를 막음).

## 로컬 실행
- 정수 맥: `~/Projects/MoA`. 앱 실행은 터미널에서 `cd backend && ./gradlew bootRun` (Docker Desktop 켜고).
- 로컬 MySQL은 compose로 **3307** 포트 (정수 맥에 다른 MySQL이 3306을 씀). 접속 `localhost:3307`, `moa` / `moa`.

## 다음 할 일
1. PR #26~#31 순서대로 머지
2. 열린 질문 9개 확정, 결정 로그 기록
3. 프론트 프레임워크 결정 (옵션 2~3개 비교 후 정수 결정), 화면 구현 (frontend-dev, prototype/ 흐름 기준)
