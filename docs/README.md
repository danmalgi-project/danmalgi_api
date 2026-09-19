# danmalgi_api 도메인 문서

`danmalgi_api` 를 처음 만지는 사람이 **코드만 봐서는 알 수 없는 것**을 모아둔 곳이다.
API 레퍼런스가 아니다. RPC 시그니처는 `src/main/proto/` 가 정본이고,
여기에는 *왜 그렇게 되어 있는지* 와 *무엇을 조심해야 하는지* 를 적는다.

---

## 읽는 순서

1. **[domain-glossary.md](01-foundation/domain-glossary.md)** — 용어부터.
   `Relation` 과 `Friendship`, `DMC` 와 `UDMC` 를 구분하지 못하면 나머지가 안 읽힌다.
2. **[system-architecture.md](01-foundation/system-architecture.md)** — 이 서버가 무엇을 책임지고
   무엇을 chat/webrtc 에 맡기는지, 서버 간 계약이 어디에 있는지.
3. **[auth-and-identity.md](02-domain/auth-and-identity.md)** — 로그인·회원가입·JWT.
   모든 RPC 가 이 위에서 돈다.
4. **[relationship-lifecycle.md](02-domain/relationship-lifecycle.md)** — 친구 요청과 친구 관계의 상태 전이.

그다음은 필요한 것부터 골라 읽으면 된다. 기능을 만지기 전에는
[grpc-contract.md](04-conventions/grpc-contract.md) 와 [error-catalog.md](04-conventions/error-catalog.md) 를 먼저 보는 편이 좋다.

---

## 디렉터리 구성

```
docs/
├── README.md              ← 여기
├── 01-foundation/         이 시스템이 무엇인가
├── 02-domain/             비즈니스 규칙
├── 03-integration/        바깥과 맞닿는 경계
└── 04-conventions/        코드를 쓸 때 지키는 것
```

번호는 **읽는 순서**이자 **추상도 순서**다. 위로 갈수록 오래 가는 지식이고,
아래로 갈수록 코드 변경에 같이 바뀐다. 새 문서는 아래 기준으로 자리를 정한다.

| 디렉터리 | 넣는 기준 |
|---|---|
| `01-foundation` | 서비스가 무엇을 다루는지. 기능이 바뀌어도 잘 안 변한다 |
| `02-domain` | "이럴 땐 이렇게 동작한다" — 상태 전이와 불변식 |
| `03-integration` | 다른 서버·외부 시스템과의 계약. 우리 쪽만 고쳐선 안 되는 것 |
| `04-conventions` | 코드를 쓰는 방법. 도메인 지식이 아니라 작업 규약 |

---

## 문서 목록

### `01-foundation/` — 기반

| 문서 | 다루는 것 |
|---|---|
| [domain-glossary.md](01-foundation/domain-glossary.md) | 용어 정의, 헷갈리는 짝 |
| [system-architecture.md](01-foundation/system-architecture.md) | 3개 서버의 책임 경계, 진실의 원천, 인터셉터 배치, 레이어 구조 |
| [data-model.md](01-foundation/data-model.md) | PostgreSQL ERD, 인덱스 현황, `ddl-auto: update` 한계, 저장소 경계, Redis 키 |

### `02-domain/` — 도메인 규칙

| 문서 | 다루는 것 |
|---|---|
| [auth-and-identity.md](02-domain/auth-and-identity.md) | OAuth → pending 세션 → Register, assigned id 전략, JWT, UserStatus |
| [relationship-lifecycle.md](02-domain/relationship-lifecycle.md) | Relation / Friendship 상태 전이, outgoing·incoming |
| [dm-channel-model.md](02-domain/dm-channel-model.md) | DMC/UDMC 분리, 1:1 중복 방지(advisory lock), 나가기 정책 |

### `03-integration/` — 외부 경계

| 문서 | 다루는 것 |
|---|---|
| [messaging-flow.md](03-integration/messaging-flow.md) | api ↔ chat 접점 3가지, 오프라인 판정, FCM 푸시, `user:{id}` 계약 |
| [webrtc-signaling.md](03-integration/webrtc-signaling.md) | api ↔ webrtc 경계, JWT 공유, `GetUsers` 응답 형태가 왜 중요한가 |
| [media-storage.md](03-integration/media-storage.md) | R2 업로드 경로, public URL vs presigned URL, 이미지 처리·검증 |

### `04-conventions/` — 작업 규약

| 문서 | 다루는 것 |
|---|---|
| [grpc-contract.md](04-conventions/grpc-contract.md) | external/internal 분리, proto 소유권, 인증 라우팅, 버저닝 |
| [error-catalog.md](04-conventions/error-catalog.md) | 도메인 예외 → gRPC status 매핑, 클라이언트 분기 지침 |
| [code-conventions.md](04-conventions/code-conventions.md) | 패키지·레이어 규칙, 엔티티/validator/mapper/예외 작성법, 테스트 컨벤션 |

---

## 빠른 참조 — 자주 찾게 되는 것

| 찾는 것 | 문서 |
|---|---|
| Relation 과 Friendship 차이 | [domain-glossary.md](01-foundation/domain-glossary.md#2-관계--relation-과-friendship-은-다른-것이다) |
| 왜 userId 를 미리 뽑는가 | [auth-and-identity.md](02-domain/auth-and-identity.md#3-userid-를-미리-뽑는-이유와-그-대가) |
| pending 상태에서 부를 수 있는 RPC | [auth-and-identity.md](02-domain/auth-and-identity.md#5-pending-상태에서-할-수-있는-일) |
| 1:1 방 중복 생성은 어떻게 막나 | [dm-channel-model.md](02-domain/dm-channel-model.md#3-11-중복-방지--advisory-lock) |
| Redis 키 전체 목록 | [data-model.md](01-foundation/data-model.md#6-redis-키-전체) |
| 예외가 어떤 status 로 나가나 | [error-catalog.md](04-conventions/error-catalog.md#2-도메인-예외-grpcexception-구현) |
| 새 internal 서비스 추가 절차 | [grpc-contract.md](04-conventions/grpc-contract.md#3-인증-라우팅) |
| 테스트 파일 이름 규칙 | [code-conventions.md](04-conventions/code-conventions.md#7-테스트) |
| 프로필 URL 은 왜 만료가 없나 | [media-storage.md](03-integration/media-storage.md#3-url-발급--여기가-핵심) |

---

## 알려진 빈틈 모음

문서 곳곳에 ⚠️ 로 표시해 두었다. 운영 전에 판단이 필요한 것들:

| 항목 | 문서 |
|---|---|
| internal gRPC 가 무인증, external 과 같은 8080 포트 | [grpc-contract.md](04-conventions/grpc-contract.md#3-인증-라우팅) |
| JWT 에 만료가 없다 (리프레시 체계도 없음) | [auth-and-identity.md](02-domain/auth-and-identity.md#6-jwt) |
| `INTERNAL` 응답에 내부 예외 메시지가 그대로 나간다 | [error-catalog.md](04-conventions/error-catalog.md#4-description-에-내부-정보가-그대로-나간다) |
| `friends` / `relations` 에 UNIQUE 제약 없음 → 중복 가능 | [relationship-lifecycle.md](02-domain/relationship-lifecycle.md#8-알려진-빈틈-정리) |
| `devices.device_id` UNIQUE 없음 → 죽은 FCM 토큰 | [messaging-flow.md](03-integration/messaging-flow.md#7-알려진-빈틈) |
| 차단(`BLOCK`)이 메시지·통화에 실효되지 않는다 | [relationship-lifecycle.md](02-domain/relationship-lifecycle.md#5-friendship-상태) |
| 친구가 아니어도 DM 채널을 만들 수 있다 | [dm-channel-model.md](02-domain/dm-channel-model.md#2-채널-생성) |
| 인덱스가 사실상 없다 | [data-model.md](01-foundation/data-model.md#3-인덱스-현황) |
| 업로드한 이전 이미지가 R2 에 계속 쌓인다 | [media-storage.md](03-integration/media-storage.md#7-알려진-빈틈) |
| `GetUsers` 응답 형태를 바꾸면 webrtc 가 패닉한다 | [webrtc-signaling.md](03-integration/webrtc-signaling.md#4-user_storegetusers-의-동작이-webrtc-안정성에-직결된다) |
| `application.yaml` 에 평문 시크릿 | — |

---

## 이 문서들을 고치는 기준

- **코드를 읽으면 알 수 있는 것은 쓰지 않는다.** 메서드 목록, 필드 나열은 불필요하다.
- **왜 그렇게 했는지**와 **그렇게 하지 않으면 무엇이 깨지는지**를 쓴다.
- **아직 없는 것**도 쓴다. "차단은 표시만 되고 실효되지 않는다" 같은 빈틈이
  가장 비싼 지식이다.
- 시크릿 값은 절대 넣지 않는다. 설정 **키 이름**까지만 적는다.
- 동작을 바꾸는 PR 은 관련 문서의 해당 절을 같이 고친다.
- 새 문서는 위 디렉터리 기준표에 따라 넣고, **이 README 의 목록에 한 줄 추가**한다.
  문서 간 링크는 상대 경로로 쓴다 (`../01-foundation/domain-glossary.md`).

---

## 다른 곳에 있는 문서

| 위치 | 내용 |
|---|---|
| `danmalgi_webrtc/docs/webrtc-signaling-flow.md` | 시그널링 전체 흐름 (SFU 토폴로지, offer 주체) |
| `danmalgi_chat/docs/` | 메시지 큐, FCM 푸시, Redis 키 |
| `danmalgi_backend/docs/legacy-pending-user-cleanup.md` | 레거시 `status=PENDING` 행 1회성 정리 SQL |
| `../README.md` | 실행 환경 구성, Docker, 설정 항목 |
| `danmalgi_backend/docs/harness/` | 에이전트 하네스 규약 (이 리포의 루트 README 가 가리키는 문서들의 실제 위치) |
