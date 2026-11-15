# 기능 03. 판매 글과 상품 등록

> 2026-10-13, 작성: pm, 상태: **진행 (2026-10-13, 열린 질문은 추천안으로 구현, 정수 확인 전)**, 이슈 #28
> 입력: [requirements.md](../requirements.md) F-02, F-03, F-03a, D-12, D-17, D-21, D-25 / [erd-v2.md](../erd/erd-v2.md) `sale_post`, `post_image`, `product`, 5장(에디터) / [03 리서치](../research/03-auth-image-realtime.md) 2장

## 1. 유저 스토리
- US-1. 판매자로서, 블로그 글처럼 제목, 본문, 사진으로 판매 글을 쓰고 싶다 (F-02).
- US-2. 판매자로서, 본문과 따로 상품을 번호, 이름, 가격으로 입력하고 싶다. 그래야 댓글 판정이 정확하다 (D-21).
- US-3. 판매자로서, 미리 써 두고 원하는 때에 [판매 오픈]을 눌러 공개하고 싶다.
- US-4. 구매자로서, 인스타 링크로 들어와 로그인 없이 바로 글과 상품 목록을 보고 싶다.

## 2. 수용 기준 (Given / When / Then)

### 글 작성, 수정
- AC-1. Given 판매자, When 제목, 본문, 이미지 키 목록, 상품 목록을 넣어 저장하면, Then `sale_post`(status=DRAFT), `post_image`(sort_order는 보낸 순서), `product`(status=ON_SALE)가 **한 트랜잭션**으로 생기고 201을 받는다.
- AC-2. Given 새 글, Then `shipping_fee`=3500, `remote_shipping_fee`=6000이 서버에서 채워진다. 요청 값은 받지 않는다 (D-25, 지금은 고정값).
- AC-3. Given 구매자 계정, When 글 작성 API를 부르면, Then 403 (서버에서 역할 확인).
- AC-4. Given 상품 하나, Then 번호(`item_no`), 이름, 가격이 모두 필수다. 번호는 `1`, `1-2`처럼 판매자가 자유롭게 쓴다 (ERD v2). 수량 입력칸은 없다 (D-12, 항상 1개).
- AC-5. Given 같은 글 안에서 번호가 겹치는 상품, When 저장하면, Then 400. 동시에 저장돼도 `(post_id, item_no)` 유니크가 최종 방어선이다.
- AC-6. Given 가격이 0 이하이거나 정수가 아님, 제목이나 상품 이름이 비어 있음, When 저장하면, Then 400과 틀린 필드 안내.
- AC-7. Given DRAFT 글의 작성자, When 수정하면, Then 제목, 본문, 사진, 상품을 모두 바꿀 수 있다. Given 다른 판매자, When 같은 글을 수정하면, Then 404.
- AC-8. Given OPEN 글, When 수정하면, Then 바꿀 수 있는 범위는 Q-1에 따른다.

### 판매 오픈
- AC-9. Given DRAFT 글이고 상품이 1개 이상, When 작성자가 [판매 오픈]을 누르면, Then status=OPEN, `opened_at`=서버 시각. 이때부터 댓글 주문을 받는다 (04 문서).
- AC-10. Given 상품이 0개, When 오픈하면, Then 400. Given 이미 OPEN, When 다시 오픈하면, Then 200이고 `opened_at`은 그대로 (같은 요청을 여러 번 보내도 결과 같음).
- AC-11. Given OPEN 글, Then DRAFT로 되돌릴 수 없다.

### 조회
- AC-12. Given 비로그인 사용자 포함 누구나, When OPEN 글을 조회하면, Then 제목, 본문, 사진 URL(순서대로), 판매자 닉네임, 상품 목록(번호, 이름, 가격, 상태), 배송비 안내 값을 받는다. 링크 하나로 들어와야 하므로 (2장, 5장 모바일 지연).
- AC-13. Given DRAFT 글, When 작성자가 아닌 사람이 조회하면, Then 404.
- AC-14. Given 판매자, When 내 글 목록을 조회하면, Then 내 글만 최신순으로 온다.
- AC-15. Given 글 응답, Then 판매자 계좌, 실명은 들어가지 않는다 (계좌는 05 문서에서 당첨자에게만).

### 이미지
- AC-16. Given 판매자, When 이미지 파일(jpg, png, webp)을 올리면, Then 서버 로컬 디스크에 저장되고 추측하기 어려운 키(UUID 등)와 조회 URL을 받는다 (D-17).
- AC-17. Given 허용하지 않은 형식이나 크기, 개수 상한 초과, When 올리면, Then 400. 상한 값은 Q-3.
- AC-18. Given 저장 코드, Then 파일 저장은 `ImageStorage` 같은 인터페이스 뒤에 있고, 글과 상품 코드는 로컬 경로를 직접 모른다 (D-17, 나중에 S3로 교체).
- AC-19. Given DB, Then 파일 자체가 아니라 `image_key`와 `sort_order`만 저장한다.

## 3. 제안 API
| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/images` | multipart `file` | 201, imageKey, url |
| POST | `/api/posts` | title, content, imageKeys[], products[{itemNo, name, price}] | 201, postId, status |
| PUT | `/api/posts/{postId}` | title, content, imageKeys[], products[{itemNo, name, price}] (생략 가능) | 200, 글 상세 |

- PUT의 products: DRAFT일 때만 보낼 수 있고, 보내면 상품을 통째로 바꿈(productId가 바뀜). OPEN, CLOSED 글에 보내면 409 `PRODUCTS_LOCKED`라 제목, 본문, 사진만 고칠 땐 products를 빼고 보냄 (Q-1 A)
| POST | `/api/posts/{postId}/open` | 없음 | 200, status, openedAt |
| POST | `/api/posts/{postId}/close` | 없음 (Q-2에서 A를 고를 때만) | 200, status |
| GET | `/api/posts/{postId}` | 없음 | 200, postId, title, content, status, openedAt, sellerNickname, images[{url, sortOrder}], products[{productId, itemNo, name, price, status}], shippingFee, remoteShippingFee |
| GET | `/api/me/posts` | 없음 | 200, [postId, title, status, openedAt, productCount] |
| GET | `/images/{imageKey}` | 없음 | 이미지 파일 (정적 서빙) |

- 오픈을 PUT 수정과 따로 둔 이유: 상태 전이(DRAFT에서 OPEN)를 한 곳에서만 일어나게 하려고.
- 판매자는 세션에서 꺼낸다. 요청 본문의 sellerId는 받지 않는다.
- 댓글 목록과 당첨 상태는 [04-order-claim.md](04-order-claim.md)의 피드 API에서 받는다.

## 4. 범위 밖
- 판매자가 배송비를 바꾸는 기능 (컬럼만 있고 지금은 고정값, D-25)
- 예약 오픈 (정해진 시각에 자동 OPEN)
- 글 삭제, 글 복사
- 전체 글 목록, 검색, 판매자 프로필 페이지 (구매자는 링크로 들어옴)
- 블록 에디터, 본문 안 이미지 삽입 (ERD 5장: 단순 textarea + 이미지)
- 이미지 리사이즈, 썸네일, CDN, S3 (D-17)
- 안 쓰인 이미지 파일 정리 (Q-3에서 A를 고르면 생김)
- 상품 수량 N개 (D-12)

## 5. 열린 질문 (정수 결정 필요)
| ID | 질문 | 옵션 (트레이드오프) | 추천 |
|---|---|---|---|
| Q-1 | OPEN 후 무엇을 고칠 수 있나? | A. 제목, 본문, 사진만 수정, 상품은 추가, 수정, 삭제 모두 불가 (판정 기준이 안 흔들림, 오타 난 상품명을 못 고침) / B. 진행 중 당첨이 없는 상품만 수정, 삭제, 추가 가능 (유연하지만 이미 달린 댓글의 매칭이 이름 변경으로 어긋날 수 있음) / C. OPEN 후 아무것도 못 고침 (가장 단순, 본문 오타도 못 고침) | A |
| Q-2 | 판매 종료(CLOSED)를 지금 만드나? | A. 판매자 [판매 종료] 버튼. 이후 새 댓글은 막고, 진행 중 당첨은 끝까지 진행, 만료돼도 승계 안 함 (끝난 글에 주문이 계속 오는 문제 해결, 06 승계에 조건 하나 추가) / B. 지금은 없음, 글은 계속 OPEN (가장 단순, 상품이 ON_SALE로 돌아오면 몇 주 뒤에도 당첨이 생김) | A |
| Q-3 | 이미지 업로드 방식 | A. 이미지를 먼저 올려 키를 받고, 글 저장 때 키 목록만 보냄 (미리보기, 수정이 쉬움, 글을 안 쓰면 파일이 남음) / B. 글 저장 요청에 파일을 multipart로 함께 (남는 파일 없음, 요청이 크고 수정 때 사진을 다시 보내야 함). 공통 제안: 1장 10MB, 글당 10장 (설정값이라 바꾸기 쉬움) | A |

## 6. 관련 결정
F-02, F-03, F-03a, D-12, D-17, D-21, D-25, D-27(ERD v2), D-34(판매자 계좌는 가입 때 필수)

## 7. 면접 질문 예시
"이미지 저장을 인터페이스로 감싸 두면, 나중에 로컬 디스크에서 S3로 바꿀 때 무엇이 바뀌고 무엇이 안 바뀌나요?" (답의 방향: 구현체 하나와 설정만 바뀌고, 글 저장 로직과 DB의 `image_key`는 그대로다. 저장소 키와 공개 URL을 분리해 두는 것이 핵심이다)
