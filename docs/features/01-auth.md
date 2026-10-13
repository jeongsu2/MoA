# 기능 01. 회원가입과 로그인

> 2026-10-13, 작성: pm, 상태: **확정 (2026-10-13, 열린 질문 모두 추천안, D-34)**. `login-id/exists`, `PATCH /api/me`는 이번 범위에서 뺌 (오버엔지니어링 금지)
> 입력: [requirements.md](../requirements.md) F-01, F-01a, F-01c, D-01, D-05, D-14, D-16, D-19, D-24 / [erd-v2.md](../erd/erd-v2.md) `users`, `seller_profile`, `address` / [03 리서치](../research/03-auth-image-realtime.md) 1장

## 1. 유저 스토리
- US-1. 구매자로서, 인스타 링크로 들어와 한 화면에서 가입하고 싶다. 그래야 판매 오픈 전에 댓글 달 준비를 끝낼 수 있다.
- US-2. 구매자로서, 가입 때 실명, 전화번호, 기본 배송지를 한 번만 넣고 싶다. 그래야 주문마다 개인정보를 댓글에 쓰지 않는다 (D-01).
- US-3. 판매자로서, 구매자와 다른 가입 화면에서 가입하고 입금받을 계좌를 등록하고 싶다. 그래야 당첨자에게 계좌가 보인다 (D-19, D-24).
- US-4. 사용자로서, 로그인, 로그아웃하고 내 정보를 보고 싶다.
- US-5. 판매자로서, 계좌 정보를 나중에 고치고 싶다.

## 2. 수용 기준 (Given / When / Then)

### 가입
- AC-1. Given 비로그인 사용자, When 구매자 가입에 아이디, 비밀번호, 닉네임, 실명, 전화번호, 기본 배송지, 동의 2개를 모두 넣고 제출하면, Then `users`(role=BUYER, status=ACTIVE)와 `address`(is_default=true) 1개가 **한 트랜잭션**으로 생기고 201을 받는다.
- AC-2. Given 판매자 가입 화면, When 위 정보에 계좌(은행, 계좌번호, 예금주)를 더해 제출하면, Then `users`(role=SELLER)와 `seller_profile`이 같이 생긴다. (기본 배송지, 계좌 필수 여부는 Q-2, Q-3)
- AC-3. Given 동의 2개 중 하나라도 체크 안 함, When 제출하면, Then 400이고 아무 행도 생기지 않는다. 체크하면 `privacy_agreed_at`, `third_party_agreed_at`에 **서버 시각**이 저장된다 (D-14).
- AC-4. Given 이미 쓰는 아이디, When 가입하면, Then 409이고 행이 생기지 않는다. 같은 아이디로 동시에 두 요청이 와도 한 명만 성공한다 (`login_id` 유니크가 최종 방어선).
- AC-5. Given 필수값 누락이나 형식 오류(전화번호, 우편번호), When 제출하면, Then 400과 어느 필드가 틀렸는지 알려준다.
- AC-6. Given 가입 성공, Then 비밀번호는 해시(BCrypt 등)로만 저장되고 응답, 로그에 평문이 남지 않는다.
- AC-7. Given 가입 성공, When 응답을 받으면, Then 바로 로그인된 상태가 된다. (자동 로그인 여부는 Q-6)

### 로그인, 로그아웃
- AC-8. Given 가입한 사용자, When 맞는 아이디와 비밀번호로 로그인하면, Then 서버 세션이 생기고 `HttpOnly`, `SameSite` 쿠키를 받는다 (D-16). 로그인 전 세션 id는 새로 바뀐다 (세션 고정 방지).
- AC-9. Given 틀린 아이디 또는 비밀번호, When 로그인하면, Then 401이고 "아이디 또는 비밀번호가 틀렸습니다" 한 문구만 보여 어느 쪽이 틀렸는지 알려주지 않는다.
- AC-10. Given status=SUSPENDED 계정, When 로그인하면, Then 403. 이미 로그인된 세션이 있으면 다음 요청부터 막힌다 (세션을 고른 이유, D-16).
- AC-11. Given 로그인 상태, When 로그아웃하면, Then 서버 세션이 삭제되고, 같은 쿠키로 다시 요청하면 401.

### 내 정보
- AC-12. Given 로그인 상태, When 내 정보를 조회하면, Then 아이디, 역할, 닉네임, 실명, 전화번호, 기본 배송지 요약을 받는다. 판매자면 계좌 정보도 받는다. 비밀번호 해시는 절대 안 나간다.
- AC-13. Given 비로그인, When 내 정보나 다른 로그인 필요 API를 부르면, Then 401 (로그인 페이지로 리다이렉트하지 않고 JSON).
- AC-14. Given 판매자, When 계좌 정보를 수정하면, Then `seller_profile`이 바뀐다. Given 구매자, When 같은 API를 부르면, Then 403 (서버에서 역할 확인).

## 3. 제안 API
| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/auth/signup/buyer` | loginId, password, nickname, realName, phone, address{recipientName, recipientPhone, zipCode, address1, address2, deliveryNote}, privacyAgreed, thirdPartyAgreed | 201, userId, role |
| POST | `/api/auth/signup/seller` | 구매자 필드 + bankName, accountNumber, accountHolder | 201, userId, role |
| GET | `/api/auth/login-id/exists?loginId=` | (쿼리) | 200, exists |
| POST | `/api/auth/login` | loginId, password | 200, userId, role, nickname (쿠키 발급) |
| POST | `/api/auth/logout` | 없음 | 204 |
| GET | `/api/me` | 없음 | 200, loginId, role, nickname, realName, phone, defaultAddress, sellerProfile(판매자만) |
| PATCH | `/api/me` | nickname, phone 등 (범위는 Q-5) | 200, 바뀐 내 정보 |
| PUT | `/api/me/seller-profile` | bankName, accountNumber, accountHolder | 200, sellerProfile |
| GET | `/api/auth/csrf` | 없음 | 200, CSRF 토큰 (Q-7에서 A를 고를 때만) |

- 가입 API를 둘로 나눈 이유: 필드와 검증이 다르고, 요청 본문의 role 값을 믿지 않기 위해서 (구매자가 role=SELLER를 보내는 공격 차단).
- 배송지 목록, 추가, 수정은 [02-address.md](02-address.md).

## 4. 범위 밖
- 소셜 로그인 (D-16, 나중에)
- 전화번호 SMS 인증, 이메일 인증, 본인 인증 (Q-4)
- 비밀번호 찾기, 변경, 회원 탈퇴, 동의 철회 처리 (D-14 장애 시나리오, 별도 기능으로)
- 약관 문구 (D-14)
- 관리자의 계정 정지 화면 (status 컬럼만 쓰고 화면은 없음)
- 다중 서버 세션 공유(Redis), 로그인 실패 횟수 잠금
- 계좌가 댓글에 보이는 방식 (댓글, 당첨 기능 문서에서)

## 5. 열린 질문 (정수 결정 필요)
| ID | 질문 | 옵션 (트레이드오프) | 추천 |
|---|---|---|---|
| Q-1 | 판매자도 다른 판매자 글에서 구매할 수 있나? (D-19 장애 시나리오, ERD는 role 하나) | A. 판매자도 구매 가능, role은 하나로 두고 "판매자는 구매 권한 포함"으로 해석 (ERD 그대로, 권한 규칙만 정함) / B. 판매자는 판매만 (가장 단순, 인플루언서가 구매하려면 계정 2개) / C. 역할 여러 개 (`user_role` 테이블, ERD v3 필요) | A |
| Q-2 | 판매자 가입 때 기본 배송지도 필수인가? | A. 필수 (F-01a 원문 그대로, Q-1이 A면 필요) / B. 선택, 처음 구매 전에 입력 (가입이 짧아지지만 입금완료 때 기본 배송지가 없는 경우 처리 필요) | Q-1이 A면 A |
| Q-3 | 판매자 계좌는 언제 받나? | A. 가입 때 필수 (흐름 단순, 가입이 길어짐) / B. 가입 후, 글을 OPEN 하기 전에 필수 (가입은 짧지만 OPEN 검증 추가) | A |
| Q-4 | 전화번호 인증을 하나? | A. 형식 검사만 (MVP에 맞고 비용 없음, 가짜 번호 가능) / B. SMS 인증 (가짜 계정 줄지만 외부 연동, 비용) | A |
| Q-5 | 실명을 가입 후 고칠 수 있나? | A. 못 고침 (판매자 대조가 안정적, 오타 낸 사용자 불편) / B. 진행 중 당첨이 없을 때만 고침 (유연하지만 검사 로직 추가) | B |
| Q-6 | 가입 성공 후 바로 로그인 상태로 만드나? | A. 자동 로그인 (인스타 유입 이탈 감소) / B. 로그인 화면으로 이동 (구현 단순) | A |
| Q-7 | CSRF 방어 방법 (세션 쿠키라 필요) | A. Spring Security CSRF 토큰 (`CookieCsrfTokenRepository`, 표준이고 면접 설명 쉬움, 프론트가 헤더를 붙여야 함) / B. `SameSite=Lax` 쿠키만 (간단하지만 브라우저 의존) | A |
| Q-8 | 닉네임 중복 허용? | A. 유니크 (댓글에서 사람 구분이 쉬움, ERD에 UK 추가 필요) / B. 중복 허용 (가입 쉬움, 판매자는 실명으로 대조) | A |
| Q-9 | 세션 만료 시간 | A. 30분 (Spring 기본, 판매 기다리다 끊길 수 있음) / B. 길게(예: 7일) + 재로그인 유도 (편하지만 탈취 시 위험이 김) | B |

## 6. 관련 결정
F-01, F-01a, F-01c, D-01, D-05, D-14, D-16, D-19, D-24, D-27(ERD v2)

## 7. 면접 질문 예시
"세션 로그인에서 로그인 직후 세션 id를 새로 바꾸는 이유는 무엇인가요?" (답의 방향: 세션 고정 공격. 공격자가 미리 심어둔 세션 id가 로그인 후에도 살아 있으면 그 세션을 가로챌 수 있다)
