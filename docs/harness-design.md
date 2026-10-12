# Phase 4. 하네스 설계

> 2026-10-12, 상태: **확정 (2026-10-12, 정수, 5장 모두 추천안)**, 결정 기록: [ADR-003](decisions/ADR-003-harness.md). 팀 구성은 [ADR-001](decisions/ADR-001-team-composition.md)(7개)로 확정됨
> 초안 파일 위치: `/mnt/project-files/harness/` (레포 루트로 옮김)

## 1. 하네스가 뭘 하나
Claude 혼자 다 하지 않고, 역할이 다른 서브에이전트가 서로를 검증하게 만든다. 목표는 두 가지다.
- **틀리면 안 되는 것(당첨 1명, 5분 만료)을 다른 눈으로 한 번 더 본다:** qa, reviewer
- **결정마다 면접 질문으로 되묻는다:** interviewer

## 2. 파일 구성
| 파일 | 역할 |
|---|---|
| `CLAUDE.md` | 레포에서 일하는 모든 Claude가 먼저 읽는 규칙 (결정 로그, ERD, git, 글쓰기) |
| `.claude/agents/pm.md` | 기능 문서(유저 스토리, 수용 기준, 범위 밖) |
| `.claude/agents/architect.md` | 설계 옵션, ADR 초안. 승인된 ERD는 v3 제안으로만 |
| `.claude/agents/backend-dev.md` | Spring Boot 구현과 테스트. ERD, 기능 문서 없으면 멈춤 |
| `.claude/agents/frontend-dev.md` | 모바일 웹 화면. 프로토타입 흐름 기준 |
| `.claude/agents/qa.md` | 테스트 계획, 동시성 테스트, 부하 테스트 숫자 |
| `.claude/agents/reviewer.md` | diff 리뷰, 고치지 않고 지적만 |
| `.claude/agents/interviewer.md` | 단계 끝마다 꼬리질문, 답 피드백 |
| `.githooks/commit-msg` | 커밋 형식, Claude trailer, 가운뎃점 검사 |
| `.githooks/pre-push` | develop, main 직접 푸시 차단 |

### 역할별 도구 권한 (왜 다르게 주나)
| 에이전트 | 쓰기 | 실행(Bash) | 웹 검색 | 이유 |
|---|---|---|---|---|
| pm | 문서만 | 없음 | 없음 | 코드에 손대지 않게 |
| architect | 문서만 | 없음 | 있음 | 근거 자료가 필요 |
| backend-dev, frontend-dev | 있음 | 있음 | 없음 | 구현과 테스트 실행 |
| qa | 테스트, 문서 | 있음 | 없음 | 테스트 작성과 실행 |
| reviewer | **없음** | git diff용 | 없음 | 리뷰어가 직접 고치면 리뷰가 아니게 됨 |
| interviewer | 없음 | 없음 | 있음 | 질문만 |

- 면접 질문 예시: "서브에이전트마다 도구 권한을 다르게 준 이유는 무엇인가요?" (답의 방향: 최소 권한 원칙, 역할 섞임 방지, 리뷰어가 고치면 검증이 사라짐)

## 3. 기능 하나의 흐름
```
pm ─▶ (architect) ─▶ qa(테스트 계획) ─▶ backend-dev ─▶ (frontend-dev) ─▶ qa(검증) ─▶ reviewer ─▶ (interviewer)
기능 문서     설계 변경 시만      실패할 테스트 먼저      구현+테스트        화면                 테스트, 부하       diff 리뷰      꼬리질문
```
괄호는 필요할 때만. 단계마다 정수 확인 후 다음으로 간다.

## 4. 자동 검사 (git hook)
`git config core.hooksPath .githooks` 한 번이면 정수와 Claude 모두에게 적용된다.
- `commit-msg`: 제목이 `type : 변경` 형식인지, `Co-Authored-By`, `Claude-Session` trailer가 있는지, 가운뎃점이 있는지.
- `pre-push`: develop, main으로 직접 푸시 차단.
- 로컬에서 확인함: 형식 맞는 커밋 통과, "로그인 추가" 차단, trailer 차단, 가운뎃점 차단, develop 푸시 차단.

## 5. 결정 (2026-10-12, 모두 ⭐ 추천안)
1. **하네스 파일을 레포에 올릴지:** A. 올림 ⭐추천 (하네스 자체가 포트폴리오, 다른 기기에서도 같은 규칙) / B. 로컬에만
2. **자동 검사 방식:** A. git hook ⭐추천 (사람, Claude 모두 적용) / B. Claude Code hook (Claude만) / C. 없음
3. **에이전트 호출 범위:** A. 기능마다 7개 전부 (가장 꼼꼼, 토큰 많음) / B. pm, qa, backend-dev, reviewer는 매번, 나머지는 필요할 때 ⭐추천

## 6. Phase 5 들어가기 전 남은 것
- 빌드 도구(Gradle vs Maven), 프론트 프레임워크(React + Vite 등), 로컬 MySQL 실행 방식(Docker Compose)은 Phase 5 첫 단계에서 옵션으로 정한다.
