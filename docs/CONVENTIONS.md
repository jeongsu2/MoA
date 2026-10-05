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

### 백엔드
- 변수, 함수, Attribute 이름은 소문자 + 밑줄(`snake_case`), 무슨 기능인지 바로 알 수 있는 단어로
- 모듈 상수는 대문자 + 밑줄(`ALL_CAPS`)
- 들여쓰기는 공백 4칸
- 한 줄짜리 `if`, `for`, `while`, `except` 문을 쓰지 않고 여러 줄로 나눠 쓴다
- 모듈 임포트는 상대경로 대신 절대경로
- 예외는 `except:`로 뭉뚱그려 잡지 말고 예외 종류를 명시한다
- 주석: `#` 뒤에 공백 한 칸. 인라인 주석은 코드와 최소 공백 두 칸 띄운다
- 실행되는 코드만 남기고 커밋한다

### 프론트엔드
- 파일명은 기능을 알 수 있는 단어, 첫 글자 소문자, 카멜 케이스. 예) `sampleComponent.jsx`
- `let`, `const`만 사용 (`var` 금지)
- 파일 확장자는 `.jsx`
- 단위는 `rem`, `%`(또는 분수) 사용, `px` 금지
- ESLint 오류는 무시 주석을 달지 않고 고친다

## 5. 확인이 필요한 부분
- 백엔드 규칙(`except`, `snake_case`, 공백 4칸, 절대경로 임포트)은 **Python 기준**이다. 백엔드 언어는 Phase 2(ERD) 기술 선택에서 정하므로, 다른 언어로 정하면 이 장을 다시 맞춘다.
- 프론트 예시에 `sampleComponent.tsx`가 있었지만 규칙은 `.jsx`라서 `.jsx`로 적었다.
