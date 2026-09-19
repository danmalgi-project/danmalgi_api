# 도메인 용어 사전

`danmalgi_api` 코드에 등장하는 이름들이 무엇을 가리키는지 정리한다.
**이름만 보고 오해하기 쉬운 것**을 우선으로 담았다.

---

## 1. 사람

### User
서비스에 가입이 완료된 사람. `users` 테이블 1행 = 1명.

- 코드: `user/domain/model/User.java`, `user/repository/entity/UserEntity.java`
- **PK 는 애플리케이션이 지정한다.** `@GeneratedValue` 가 없고, 로그인 단계에서 시퀀스에서 미리 뽑아둔 값을 그대로 쓴다 → [auth-and-identity.md](../02-domain/auth-and-identity.md)
- 식별 축이 두 개다.
  - `(oauth_type, identify_id)` — OAuth 제공자 기준 계정 동일성. `uk_users_oauth_type_identify_id`
  - `(nickname, tag)` — 사람이 서로를 찾을 때 쓰는 공개 식별자. `uk_users_name_tag`

### Name / Tag
사용자가 직접 정하는 **공개 식별자 쌍**. 친구 추가는 id 가 아니라 `name + tag` 로 한다
(`RelationService.addRelationship`).

| | 도메인 검증 (`User.validate`) | DB 컬럼 |
|---|---|---|
| name (`nickname`) | 최대 **16자**, 공백 불가 | `varchar(32)` |
| tag | 최대 **5자**, 공백 불가 | `varchar(16)` |

> ⚠️ 도메인 상한과 컬럼 길이가 다르다. 실제 제약은 **도메인 검증 쪽(16/5)** 이 먼저 걸린다.
> 컬럼이 더 넉넉한 것은 과거 스키마의 잔재이며, 상한을 바꿀 때는 두 곳을 같이 봐야 한다.

### PendingOAuthProfile
OAuth 인증은 끝났지만 **아직 `users` 행이 없는 사람**. Redis 에만 존재한다.

- 코드: `auth/domain/model/PendingOAuthProfile.java`, `auth/infrastructure/pending/PendingAuthStore.java`
- `User` 는 `validate()` 가 name/tag 를 강제하므로 이 상태를 표현할 수 없어서 별도 모델로 뺐다.
- TTL **30분**. 이 안에 `Register` 를 마치지 않으면 사라진다.

### Device
한 사용자의 **단말 1대**. FCM 토큰의 소유 단위다.

- 코드: `device/domain/Device.java`, `device/repository/entity/DeviceEntity.java`
- `deviceId` 는 클라이언트가 만들어 보내는 값이고, **JWT 클레임에 박힌다**.
- 한 User 가 여러 Device 를 가질 수 있다 (`UserEntity.devices`, `@OneToMany`).

---

## 2. 관계 — Relation 과 Friendship 은 다른 것이다

이 프로젝트에서 가장 헷갈리는 지점이다. **둘은 별개 테이블이고 생명주기도 다르다.**

| | Relation | Friendship |
|---|---|---|
| 테이블 | `relations` | `friends` |
| 의미 | **친구 요청** (아직 친구 아님) | **성립된 친구 관계** |
| 방향 | `requester` → `receiver`, 단방향 1행 | `user` → `friend`, **양방향 2행** |
| 상태 | `PENDING / ACCEPT / REJECT / CANCEL` | `ACCEPT / BLOCK / DELETE` |
| 누가 만드나 | `AddRelationship` | Relation 이 `ACCEPT` 될 때 **자동 2행 생성** |
| 서비스 | `RelationService` | `FriendshipService` |
| proto | `external/relationship/v1` | `external/friend/v1` |

자세한 전이 규칙은 [relationship-lifecycle.md](../02-domain/relationship-lifecycle.md).

> 명명 주의: 패키지는 `relation`, proto 는 `relationship`, 엔티티는 `RelationEntity`.
> 셋 다 같은 것을 가리킨다.

---

## 3. 채팅방

### DirectMessageChannel (DMC)
**방 그 자체**. `direct_message_channels` 테이블.

- 가진 것은 `isGroup`, `channelImageUrl`, `createdAt` 뿐이다. **참여자도 방 이름도 여기 없다.**
- `isGroup = false` → 1:1, `true` → 그룹. 이 플래그는 단순 표시용이 아니라
  1:1 중복 방지 쿼리의 **정확성 조건**이다 → [dm-channel-model.md](../02-domain/dm-channel-model.md)

### UserDirectMessageChannel (UDMC)
**"누가 어느 방에 있는가" + "그 사람에게 이 방이 뭐라고 보이는가"**.
`user_direct_message_channels` 테이블, `UNIQUE(user_id, direct_message_channel_id)`.

- 코드: `udmc/` 패키지
- 참여자 목록(membership)이자, **참여자별 채널 이름**의 보관소다.
- 왜 이름이 여기 있나 — 1:1 방의 이름은 "상대방 이름"이라 **보는 사람마다 다르다.**
  `buildChannelName()` 이 자기 자신을 뺀 나머지 참여자 이름을 `,` 로 이어 붙인다.
- 방을 나가면(`leaveChannel`) **UDMC 행만 지운다.** DMC 행은 남는다.

> 약어 `udmc` 는 패키지명으로 그대로 쓰이므로 검색 키워드로 유용하다.

### LastMessage
채널 목록에 붙는 마지막 메시지 요약. **이 서버에 저장되지 않는다.**
`chat-server` 에 gRPC 로 물어본다(`chat/client/ChatMessageGrpcClient`).
조회 실패는 예외가 아니라 **빈 값으로 degrade** 한다.

---

## 4. gRPC 계약 구분

### external / internal
`src/main/proto/` 아래가 두 갈래로 갈린다. **이 구분이 인증 방식을 결정한다.**

| | `external/` | `internal/` |
|---|---|---|
| 호출자 | 앱 클라이언트 | chat-server, webrtc-server |
| 인증 | 유저 JWT (`GrpcAuthInterceptor`) | `GrpcInternalAuthInterceptor` |
| 예 | `auth`, `user`, `friend`, `relationship`, `dm`, `chat`, `signaling` | `user_store`, `dm_store`, `device_store`, `chat_store` |

- proto `package` 에는 `internal` 접두어가 **없다** (`user_store.v1`). 디렉터리와 `java_package` 에만 있다.
  그래서 이름 패턴으로 구분할 수 없고 `GrpcServiceInterceptorFilter` 의 **allowlist** 로 판별한다.
- allowlist 는 fail-closed 다 — 등록하지 않은 새 서비스는 자동으로 유저 JWT 인증 대상이 된다.

### `*_store`
"다른 서버가 이 서버의 데이터를 읽어가는 창구". 비즈니스 로직이 아니라 **조회 전용 어댑터**다.
`store/` 패키지의 클래스들은 기존 도메인 서비스를 감싸기만 한다.

> `chat_store` 만 방향이 반대다 — **이 서버가 chat-server 를 호출할 때** 쓰는 클라이언트 측 proto다.

---

## 5. 인프라 용어

| 용어 | 뜻 |
|---|---|
| **R2** | Cloudflare R2. 프로필/채널 이미지 저장소. DB 에는 **key** 만 넣고 응답 시 public URL 로 조립한다 (`applyPublicProfileImageUrl`) |
| **advisory lock** | PostgreSQL `pg_advisory_xact_lock`. 1:1 채널 중복 생성 방어용. 참여자 집합을 FNV-1a 64bit 해시한 값을 키로 쓴다 (`ParticipantsLockKey`) |
| **`user:{id}` / `device:{deviceId}`** | Redis 공유 캐시 키. **chat/webrtc 서버가 직접 읽는 계약**이라 형식을 임의로 못 바꾼다. TTL 2분 |
| **`auth:pending:*`** | pending 세션 전용 키. `auth:` prefix 로 위 공유 계약과 충돌을 피한다 |

---

## 6. 자주 틀리는 짝

- **Relation ≠ Friendship** — 요청 / 성립된 관계
- **DMC ≠ UDMC** — 방 / 방 참여 정보(+참여자별 이름)
- **`external` ≠ `internal`** — 앱용 / 서버간용, 인증 경로가 다르다
- **`profileImageUrl` 컬럼값 ≠ 응답값** — DB 는 R2 key, 응답은 public URL
