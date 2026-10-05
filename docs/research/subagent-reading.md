# 서브에이전트 구성 참고 자료 (Phase 0 판단용)

> 2026-10-05, 목적: 팀 구성 옵션 A/B/C를 고를 근거

## 추천 순서
1. **입문: Claude Code 공식 문서 "Subagents"** — https://code.claude.com/docs/en/sub-agents
   - 서브에이전트 = 자기만의 시스템 프롬프트, 도구 권한, **별도 컨텍스트**를 가진 보조 AI. 결과는 요약만 돌려준다.
   - 정의 방법: `.claude/agents/<이름>.md` (frontmatter에 name, description, tools, model)
   - 쓰기 좋은 일: 결과가 길게 나오는 작업(테스트, 로그, 리서치), 병렬 조사, 도구 제한이 필요한 역할
   - 피할 일: 자주 주고받으며 다듬어야 하는 작업, 대화 맥락 전체가 필요한 작업
2. **설계 원칙: Anthropic "Building Effective Agents"** — https://www.anthropic.com/engineering/building-effective-agents
   - "가장 단순하게 시작하고, 필요할 때만 복잡도를 더하라." 패턴: 프롬프트 체이닝, 라우팅, 병렬화, 오케스트레이터-워커, 평가자-최적화자
3. **실전 사례: Anthropic "How we built our multi-agent research system"** — https://www.anthropic.com/engineering/multi-agent-research-system
   - 멀티 에이전트는 일반 대화보다 토큰을 약 15배 쓴다. 독립적인 여러 방향을 동시에 조사할 때만 이득.
   - 실패 사례: 단순한 일에 에이전트를 과하게 생성, 중복 작업. 해결은 지시문(브리프)을 구체적으로 쓰는 것.

## 이 자료로 본 판단 기준
| 기준 | 질문 | A(4) | B(7) | C(9) |
|---|---|---|---|---|
| 독립된 컨텍스트가 이득인가 | 그 역할이 본 대화와 분리돼야 하나? | 충분 | qa, interviewer는 "다른 관점"이 필요해 분리 가치 큼 | designer, devops는 호출이 드물어 분리 이득 작음 |
| 토큰 비용 | 호출이 늘수록 비용 증가 | 가장 적음 | 중간 | 가장 큼 |
| 학습 목적 | 면접 설명력에 기여하나? | 검증 장치 약함 | interviewer가 직접 겨냥 | B와 같고 포트폴리오 시연 효과만 추가 |
| "단순하게 시작" 원칙 | 나중에 추가할 수 있나? | A로 시작해 필요 시 추가 가능 | | |

**핵심 포인트:** 에이전트는 나중에 파일 하나로 추가, 삭제할 수 있어 되돌리기 쉬운 결정이다. 
면접 질문 예시: "멀티 에이전트를 썼다면, 단일 에이전트 대비 무엇이 나아졌고 비용은 얼마나 늘었나요?"
