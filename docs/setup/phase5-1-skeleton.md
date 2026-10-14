# Phase 5-1. Spring Boot 프로젝트 뼈대 만들기 (정수 직접)

> 2026-10-12, 결정: D-28(Gradle), D-29(Java 21), D-30(Docker Compose MySQL), D-31(정수가 IntelliJ로 생성, `backend/` 폴더)

## 0. 준비
- Docker Desktop 설치 후 실행해 두기 (앱을 켤 때 MySQL이 자동으로 뜸)
- JDK 21: IntelliJ 설정 화면의 JDK 목록에서 바로 내려받을 수 있음 (예: Eclipse Temurin 21)

## 1. 브랜치
```
git checkout develop
git pull origin develop
git checkout -b config/#이슈번호
```

## 2. IntelliJ New Project (Spring Boot)
| 항목 | 값 | 이유 |
|---|---|---|
| Name | `backend` | 레포 안에 `backend/` 폴더로 생김 |
| Location | MoA 레포 폴더 (README.md가 있는 곳) | |
| Language | Java | |
| Type | Gradle - Groovy | 자료가 가장 많음 (Kotlin DSL도 되지만 처음엔 Groovy) |
| Group | `com.moa` | |
| Artifact | `moa` | 메인 클래스가 `MoaApplication`이 됨 |
| Package name | `com.moa` | 패키지는 소문자 (CONVENTIONS) |
| JDK | 21 | D-29 |
| Java | 21 | |
| Packaging | Jar | |

**Spring Boot 버전:** 목록에서 SNAPSHOT, M, RC가 안 붙은 가장 최신 정식 버전.

**Dependencies (이것만):**
- Spring Web
- Spring Data JPA
- MySQL Driver
- Validation
- Lombok
- Docker Compose Support (← `compose.yaml`에 MySQL을 자동으로 만들어 줌)

Spring Security는 지금 넣지 않음. 넣으면 모든 API에 로그인이 걸려서, 인증 단계(D-16)에서 따로 추가함.

> IntelliJ Community 버전이라 Spring Boot 메뉴가 없으면: https://start.spring.io 에서 같은 값으로 Generate → zip을 풀어 `backend/` 폴더에 넣으면 똑같음.

## 3. 실행 확인
1. IntelliJ에서 `backend` 폴더를 프로젝트로 열기 (Gradle 동기화 끝날 때까지 기다림)
2. `MoaApplication` 실행 ▶
3. 로그에 `Started MoaApplication`이 나오면 성공. Docker Desktop에 mysql 컨테이너가 떠 있어야 함
4. 실패하면 로그 마지막 30줄을 스레드에 붙여주세요

## 4. 커밋, 푸시
```
git config core.hooksPath .githooks   # 아직 안 했다면
git add backend
git commit -m "chore : Spring Boot 프로젝트 생성"
git push -u origin config/#이슈번호
```
푸시하면 알려주세요. 제가 생성된 파일(build.gradle, compose.yaml, .gitignore)을 리뷰하고 PR 본문을 드릴게요.

- 면접 질문 예시: "Spring Boot Docker Compose 지원은 무엇을 자동으로 해주나요? 운영 환경에서도 쓰나요?" (답의 방향: 앱 시작 시 compose.yaml 서비스를 띄우고 접속 정보를 자동 설정. 개발용이고 운영에선 꺼짐)
