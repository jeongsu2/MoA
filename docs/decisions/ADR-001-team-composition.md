# ADR-001. 서브에이전트 팀 구성: 옵션 B (7개)

- 날짜: 2026-10-05 · 결정자: 정수 · 상태: 확정
- 관련: [phase0-team-simulation.md](../phase0-team-simulation.md), [subagent-reading.md](../research/subagent-reading.md)

## 결정
pm, architect, backend-dev, frontend-dev, qa, reviewer, interviewer 7개 역할을 서브에이전트로 둔다.

## 이유
- 실제 회사 팀(약 6.5명)의 핵심 역할을 재현하면서 백엔드 판단 검증에 무게를 둔다.
- `interviewer`가 결정마다 꼬리질문을 던져 학습 목적(면접 설명력)을 직접 겨냥한다.
- `qa`가 장애 시나리오를 만들어 ADR의 "장애 시나리오" 칸을 채운다.

## 버린 대안
- **A (4개):** 토큰은 가장 적지만 QA·면접 관점의 검증 장치가 없다.
- **C (9개):** designer, devops는 호출 빈도가 낮아 분리 이득보다 관리·토큰 비용이 크다. 디자인은 네이버 블로그 UI를 기준으로 frontend-dev가 맡는다.

## 장애 시나리오 / 리스크
- 에이전트를 단순한 일에도 호출해 토큰이 낭비됨 → Phase 4에서 호출 시점 규칙을 명확히 한다.
- 역할 간 산출물이 겹쳐 중복 작업 → 각 에이전트 입력·출력을 문서로 고정한다.
- 되돌리기: 에이전트 파일 하나 추가·삭제로 변경 가능 (되돌리기 쉬운 결정).
