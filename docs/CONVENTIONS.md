# MoA 협업 규칙과 코드 컨벤션

> 2026-10-05 정수님이 정한 규칙. 사람과 AI 모두 이 문서를 따른다.

## 1. 작업 흐름
1. **이슈 만들기**: 템플릿을 골라 이슈를 만든다. 제목 예) `[feat] 로그인 페이지 API 연동 및 구현`
2. **브랜치 만들기**: `[내용]/#[이슈번호]` 형식. 예) `feat/#12`, `docs/#3`
   ```bash
   git fetch origin
   git checkout -b feat/#12 origin/develop
   ```
3. **커밋**: 자주, 세부적으로. 한 커밋에는 한 가지 변경만 (3장 참고)
   ```bash
   git add [경로]
   git commit -m "feat(login) : 로그인 API 연동"
   ```
4. **푸시 전에 develop 최신 내용 받기**: 충돌은 로컬에서 해결한 뒤 푸시 (웹 에디터로 고치지 않기)
5. **푸시**: `git push origin feat/#12`. **develop, main에 직접 푸시 금지**
6. **PR 만들기**: `feat/#12 → develop`. 제목은 이슈와 같은 형식, 본문은 PR 템플릿. 본문에 `Closes #12`
7. **리뷰 받고 본인이 머지**: 리뷰는 꼼꼼히, 모르는 건 물어보기. `hotfix`만 리뷰 없이 머지 가능

## 2. 자주 쓰는 명령어

### develop 최신 내용 받아오기
| 명령어 | 설명 |
|---|---|
| `git stash` | 작업 중이던 변경사항 임시 저장 |
| `git switch develop` | develop으로 이동 |
| `git remote update` | 원격 브랜치 정보 최신화 |
| `git pull` | develop 최신 내용 가져오기 |
| `git switch [내용]/#[n]` | 작업하던 브랜치로 돌아가기 |
| `git merge develop` | develop 변경사항을 내 브랜치에 병합 |
| `git stash apply` | 임시 저장한 내 변경사항 다시 적용 |

`git merge develop`은 브랜치의 커밋을 병합하고, `git stash apply`는 내 컴퓨터에 임시 저장해 둔 작업 내용을 다시 불러온다.

### 브랜치 조작
| 명령어 | 설명 |
|---|---|
| `git branch` | 현재 브랜치 확인 |
| `git branch -a` | 모든 브랜치 확인 |
| `git switch [브랜치]` | 브랜치 이동 (없으면 `git checkout -b`로 생성) |
| `git branch [브랜치]` / `git branch -d [브랜치]` | 브랜치 생성 / 삭제 |
| `git log` | 커밋 기록과 커밋 ID 확인 |
| `git reset --hard [커밋 ID]` | 해당 커밋으로 되돌리기 (이후 커밋 삭제) |
| `git revert [커밋 ID]` | 해당 커밋을 취소하는 새 커밋 생성 (기록 유지) |
| `git stash list` / `git stash drop` | 임시 저장 목록 확인 / 최근 것 삭제 |

## 3. 커밋 메시지 컨벤션
형식: `타입(범위) : 변경 사항`. 범위(페이지 경로 또는 컴포넌트)는 생략 가능. **명령문, 현재형**으로 쓴다.
- `feat : 로그인 기능 구현` (O)
- `feat : 로그인 기능 구현 완료했습니다.` (X)

| 타입 | 언제 |
|---|---|
| ✨ `feat` | 새로운 기능 추가 또는 기능 업데이트 |
| 🔨 `fix` | 버그 또는 에러 수정 |
| ⭐️ `style` | 코드 포맷팅, 오타, 함수명 수정 등 스타일 수정 |
| 🧠 `refactor` | 코드 리팩토링 (기능은 같고 코드만 개선) |
| 📁 `file` | 파일 이동 또는 제거, 파일명 변경 |
| 🎨 `design` | 디자인, 문장 수정 |
| 🏷 `comment` | 주석 수정 및 삭제 |
| 🍎 `chore` | 개발 환경 세팅, 빌드 수정, 패키지 추가, 환경변수 설정 |
| 📝 `docs` | 문서 수정 |
| 🔥 `hotfix` | 치명적인 버그 수정 (리뷰 없이 머지 가능) |

커밋 메시지 템플릿 적용: `git config commit.template .gitmessage.txt`

## 4. 코드 컨벤션

### 백엔드 (Java, Spring Boot)
- 변수와 메서드 이름은 카멜 케이스(`camelCase`), 무슨 기능인지 바로 알 수 있는 단어로. 예) `findWinnerComment()`
- 클래스와 인터페이스 이름은 파스칼 케이스(`PascalCase`). 예) `CommentService`
- 상수(`static final`)는 대문자 + 밑줄(`ALL_CAPS`). 예) `PAYMENT_DEADLINE_MINUTES`
- 패키지 이름은 모두 소문자
- 들여쓰기는 공백 4칸
- `if`, `for`, `while`은 한 줄이라도 중괄호 `{}`를 쓰고 여러 줄로 나눠 쓴다
- import는 `*`(와일드카드)를 쓰지 않고 클래스마다 전체 경로로 쓴다
- 예외는 `catch (Exception e)`로 뭉뚱그려 잡지 말고 예외 종류를 명시한다
- 주석: `//` 뒤에 공백 한 칸. 코드 옆 주석은 코드와 최소 공백 두 칸 띄운다
- 실행되는 코드만 남기고 커밋한다

### 프론트엔드
- 파일명은 기능을 알 수 있는 단어, 첫 글자 소문자, 카멜 케이스. 예) `sampleComponent.jsx`
- `let`, `const`만 사용 (`var` 금지)
- 파일 확장자는 `.jsx`
- 단위는 `rem`, `%`(또는 분수) 사용, `px` 금지
- ESLint 오류는 무시 주석을 달지 않고 고친다

## 5. 참고
- 백엔드 언어는 Java + Spring Boot로 정했다 ([ADR-002](decisions/ADR-002-backend-language.md)). 처음 받은 규칙은 Python 문법 기준이라 같은 의도를 Java 표준에 맞게 옮겼다.
- 프론트 예시에 `sampleComponent.tsx`가 있었지만 규칙이 `.jsx`라서 `.jsx`로 적었다.
