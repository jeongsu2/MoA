# ADR-003. 하네스 구성: 레포에 올리고, git hook으로 검사, 핵심 에이전트만 매번 호출

- 날짜: 2026-10-12, 결정자: 정수, 상태: 확정
- 관련: [harness-design.md](../harness-design.md), [ADR-001](ADR-001-team-composition.md)

## 결정
1. `CLAUDE.md`, `.claude/agents/*.md`(7개), `.githooks/`를 레포에 올린다.
2. 규칙 검사는 git hook(`commit-msg`, `pre-push`)으로 한다. 사람과 Claude 모두에게 적용된다.
3. 기능마다 pm, qa, backend-dev, reviewer는 매번 호출하고, architect(설계가 바뀔 때), frontend-dev(화면 작업), interviewer(단계 끝)는 필요할 때만 호출한다.

## 이유
- 하네스 자체가 "AI와 어떻게 협업했는지" 보여주는 포트폴리오다. 다른 기기에서 클론해도 같은 규칙이 적용된다.
- Claude만 검사하면 사람 커밋이 규칙을 어길 수 있다. git hook은 누가 커밋하든 같은 기준.
- 7개를 매번 부르면 토큰이 많이 들고 단순한 기능에 과하다("가장 단순하게 시작", Anthropic Building Effective Agents). 정합성 검증(qa, reviewer)은 빠지면 안 된다.

## 버린 대안
- 하네스를 로컬에만: 기록이 남지 않고 기기마다 규칙이 달라짐.
- Claude Code hook만: Claude 커밋만 검사됨.
- 7개 전부 매번: 가장 꼼꼼하지만 비용이 큼.

## 장애 시나리오 / 리스크
- git hook은 `core.hooksPath` 설정을 안 하면 동작하지 않음 → README와 CLAUDE.md에 설치 한 줄 명시. GitHub 웹에서 만든 커밋(머지 등)은 검사되지 않음.
- `git commit --no-verify`로 우회 가능 → 리뷰어가 커밋 형식도 본다.
- 필요할 때만 부르는 에이전트를 빠뜨림 → 단계 끝 체크리스트에 interviewer 호출 포함.
