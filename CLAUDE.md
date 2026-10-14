# MoA 작업 규칙 (Claude용)

MoA는 인플루언서 댓글 선착순 플리마켓 웹앱이다. 정수의 백엔드 면접용 심화 학습 프로젝트라서, 기능보다 **설계 이유를 설명할 수 있는 것**이 목표다.

## 먼저 읽을 것
- **작업 현황: `docs/STATUS.md`** (어디까지 했는지, 브랜치 순서, 확인 대기 항목)
- 결정 로그: `docs/requirements.md` 8장 (D-xx). 이미 정한 것을 다시 묻지 않는다.
- ERD: `docs/erd/erd-v2.md` (승인됨). 구조를 바꾸려면 architect가 v3 제안을 쓰고 정수가 결정한다.
- 컨벤션: `docs/CONVENTIONS.md`

## 일하는 방식
- 결정은 정수가 한다. 옵션 2~3개와 트레이드오프를 내고 추천은 해도 된다.
- 새 결정은 결정 로그에 이유, 버린 대안, 장애 시나리오와 함께 남긴다. 큰 결정은 ADR.
- 새 개념이 나오면 면접 질문 예시 하나를 붙인다.
- 하네스 사용 기록은 `docs/HARNESS.md`에 남긴다.
- **오버엔지니어링 금지 (정수 요청).** 함께 정한 것(D-xx, 기능 문서 수용 기준)과 꼭 필요한 것만 만든다. 더 넣고 싶으면 만들지 말고 옵션으로 묻는다.
- 기능이 끝나면 `docs/learning/`에 학습 포인트(핵심 개념, 면접 질문 3개)를 남긴다.
- 단계마다 지금 Phase를 말하고, 정수 확인 후 다음으로 간다.

## 기능 하나의 흐름
pm(기능 문서) → architect(설계가 바뀔 때만) → qa(테스트 계획) → backend-dev → frontend-dev → qa(검증) → reviewer → interviewer

## git
- 이슈 → `[type]/#번호` 브랜치(develop에서) → 최소 단위 커밋 `type : 변경` → develop으로 PR → 정수가 머지.
- 커밋에 Co-Authored-By 등 Claude trailer 금지. author는 레포 로컬 설정 그대로.
- 이슈, PR, GitHub 댓글은 Claude가 만들지 않는다. 제목과 본문 초안, compare 링크만 전달한다.
- 이슈, PR 본문은 짧게, "~함", "~기록"으로 끝낸다.
- 처음 클론하면 `git config core.hooksPath .githooks` (커밋 형식, trailer, 직접 푸시 검사).

## 글쓰기
- 짧고 쉬운 한국어. 가운뎃점(·) 금지, 나열은 쉼표나 "와/과".
