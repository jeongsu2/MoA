# MoA Backend
마이크로 인플루언서를 위한 플리마켓 사이트. 이 레포는 Spring Boot API와 기획, 설계 문서(`docs/`), 하네스를 둔다.

## 레포 구성 (moamarket)
| 레포 | 내용 |
|---|---|
| Backend (여기) | Spring Boot API, 기획과 설계 문서, 하네스 |
| [Frontend](https://github.com/moamarket/Frontend) | 모바일 웹 화면, 흐름 프로토타입 |
| [Infra](https://github.com/moamarket/Infra) | 배포 설정 (배포 시작 때 채움) |

## 실행
Docker Desktop을 켜고 레포 루트에서:
```
./gradlew bootRun   # 로컬 MySQL(3307)이 compose로 같이 뜸
./gradlew test      # Testcontainers MySQL
```

## 처음 클론했다면
```
git config core.hooksPath .githooks
```
커밋 메시지 형식(`type : 변경`)과 develop, main 직접 푸시를 검사하는 hook이 켜진다.

## 문서
- 작업 현황: `docs/STATUS.md`
- 결정 로그: `docs/requirements.md` 8장, ADR: `docs/decisions/`
