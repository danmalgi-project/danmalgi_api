# 코드 컨벤션

이 리포에서 이미 지켜지고 있는 규칙. 새 코드를 기존 코드처럼 보이게 하는 데 필요한 것만 적는다.

---

## 1. 패키지 구조

도메인별로 자른 뒤, 그 안을 레이어로 자른다. **레이어 우선이 아니다.**

```
com.danmalgi.backend
├── auth  friendship  relation  user  device  dm  udmc   ← 도메인
├── chat       ← 다른 서버를 부르는 클라이언트
├── store      ← internal proto 어댑터
└── global     ← 공통 (security, grpc, exceptionhandler, infrastructure)
```

각 도메인 안:

```
grpc/
  XxxGrpc.java          ← @GrpcService, StreamObserver 는 여기까지
  mapper/               ← proto ↔ domain 변환 (static 메서드)
  validator/            ← @Component, 요청 필드 검증
service/
  XxxService.java       ← @Service, @Transactional
domain/
  model/                ← 프레임워크 의존 없는 순수 객체
  exception/            ← GrpcException 구현
repository/
  entity/               ← JPA 엔티티
  persistence/          ← JpaRepository 인터페이스
infrastructure/         ← 외부 시스템 어댑터 (있는 도메인만)
```

### 예외 두 가지
- `store/` 는 internal proto 어댑터일 뿐이라 `grpc/` 만 있다. 자체 service/domain 을 두지 않고
  기존 도메인 서비스를 감싼다
- `chat/` 은 서버가 아니라 **클라이언트**다. `client/{mapper,interceptor,config}` 구조

### 도메인을 새로 만드는 기준
`udmc` 가 `dm` 에서 분리되어 있는 것처럼, **독립된 테이블과 독립된 불변식**을 가지면 나눈다.
`udmc` 는 "참여자 집합" 이라는 자기 불변식(advisory lock, 정확히 일치하는 참여자)을 지킨다.

---

## 2. 레이어 경계

| 규칙 | 어기면 |
|---|---|
| proto 타입은 `grpc/` 를 넘지 않는다 | service 가 프로토콜에 묶인다 |
| `StreamObserver` 도 `grpc/` 안에서만 | 테스트가 어려워진다 |
| `domain/model` 은 Spring·JPA·proto 를 모른다 | 순수 단위 테스트 불가 |
| repository 는 entity 를, service 는 domain model 을 다룬다 | 변환 지점이 흩어진다 |

### 패키지 순환을 만들지 않는다
실제 사례 — `UserEntity.registerNew` 가 `PendingOAuthProfile` 대신 원시 타입을 받는다.

```java
public static UserEntity registerNew(Long id, String email, String name, String tag,
                                     String identifyId, int oauthType, String profileImageUrl)
```

`user/repository` → `auth/domain` 역방향 의존을 피하기 위해서다.
인자가 길어도 순환보다 낫다는 판단이 이미 내려져 있다.

---

## 3. 엔티티

```java
@Entity
@Table(name = "friends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendshipEntity {
    // 필드...
    public static FriendshipEntity of(...) { ... }   // 정적 팩토리
    public void updateStatus(int status) { ... }     // 의도가 드러나는 변경 메서드
}
```

- **`@Setter` 를 쓰지 않는다.** 변경은 `updateXxx()` 로 이름을 붙인다
- 기본 생성자는 `PROTECTED` (JPA 요구사항 충족 + 외부 생성 차단)
- 생성은 `of()` / `from()` / `registerNew()` 같은 **정적 팩토리**
- enum 은 `@Enumerated` 대신 **`int` 컬럼**으로 저장하고 도메인 enum 의 number 와 맞춘다
- `domain/model` 객체는 Redis 직렬화 대상일 수 있어 setter 와 기본 생성자가 필요할 수 있다
  (`PendingOAuthProfile`). 엔티티와 규칙이 다르다

### 이름이 다른 메서드는 주석으로 이유를 남긴다
`UserEntity.isNew`, `@PostPersist markNotNew()` 처럼 프레임워크 사정으로 존재하는 코드에는
**왜 필요한지**를 Javadoc 으로 붙인다. 이 리포는 그렇게 하고 있다.

---

## 4. validator

```java
@Component
public class XxxGrpcValidator {
    public void validateYyyRequest(Proto.YyyRequest request) {
        if (request.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
```

- **`IllegalArgumentException` 만 던진다** → 전부 `INVALID_ARGUMENT`
- 검사 범위는 **형식**뿐: blank, 양수, `UNRECOGNIZED`, 크기, 확장자, 중복 원소
- "존재하는가 / 권한이 있는가" 는 service 가 도메인 예외로 처리한다
- 메시지는 **영문 소문자**, 필드명을 proto 표기(`friend_ids`, `dm_id`)로 적는다
- proto3 는 unset 과 기본값을 구분하지 않으므로 그 경계를 validator 가 대신 만든다

---

## 5. mapper

```java
public class UserGrpcMapper {
    public static UserProto.User toProtoUser(User user) { ... }
}
```

- **static 메서드만.** 상태를 갖지 않는다 (빈으로 등록하지 않는다 — validator 와 다른 점)
- 이름은 `toProtoXxx` / `toDomainXxx`
- enum 은 number 로 맞춘다. `forNumber()` 결과가 `UNRECOGNIZED` 일 수 있음을 염두에 둔다

---

## 6. 예외

```java
public class XxxException extends RuntimeException implements GrpcException {
    private final String description;   // @RequiredArgsConstructor
    @Override public Status getStatus() { return Status.NOT_FOUND; }
    @Override public String toDescription() { return description; }
}
```

- 도메인 예외는 **`GrpcException` 을 구현해 status 를 스스로 선언한다**
- `RuntimeException` 상속 (checked 예외를 쓰지 않는다)
- 메시지에 식별자를 넣는다: `"relationship not found: id=" + relationId`
- ⚠️ 이 메시지는 **클라이언트까지 그대로 나간다** → [error-catalog.md](error-catalog.md#4-description-에-내부-정보가-그대로-나간다)

---

## 7. 테스트

### 파일 하나 = 메서드 하나
```
src/test/java/.../udmc/service/UserDirectMessageChannelServiceLeaveChannelTest.java
                            └ 대상 클래스 ──────────────────┘└ 메서드 ─┘
```
`{클래스명}{메서드명}Test`. 패키지는 `src/main` 을 그대로 미러링한다.
클래스가 커지면 테스트 파일이 여러 개로 늘어난다 — 의도된 구조다.

### 테스트 이름은 한글
```java
@Test
void of_참여자_순서가_달라도_같은_키를_반환한다() { ... }

@Test
void of_null이면_IllegalArgumentException() { ... }
```
`{메서드}_{상황}_{기대}`. `@DisplayName` 을 쓰지 않고 메서드명으로 해결한다.

### 도구
- **AssertJ** (`assertThat`, `assertThatThrownBy`). JUnit assertion 을 쓰지 않는다
- 테스트 클래스는 `public` 이 아니다 (package-private)
- fixture 는 `{도메인}/fixture/XxxFixture.java` 에 static 팩토리로 (`UserFixture`)

### 무엇을 테스트하나
현재 커버리지는 **service·grpc·mapper·validator·infrastructure 전반**이다.
특히 아래처럼 **왜 그렇게 짰는지를 고정하는 테스트**가 있다:

| 테스트 | 지키는 것 |
|---|---|
| `ParticipantsLockKeyOfTest#of_64비트_전_구간을_사용한다` | 32bit 해시로 되돌아가지 못하게 |
| `UserEntityIsNewTest` / `MarkNotNewTest` | `merge` 로 새는 것 방지 |
| `GrpcServiceInterceptorFilterTest` | internal/external 라우팅 |
| `RedisConfigRedisCacheManagerTest` | 키 접두사·TTL 계약 |
| `R2UploaderToPublicUrlTest` | URL 조립 규칙 |

> 이 계열 테스트를 지우면 문서에 적힌 제약이 조용히 무너진다.
> 동작을 바꿀 때는 테스트를 **고치는 것이 아니라 왜 바뀌는지 판단**해야 한다.

---

## 8. 주석

이 리포는 주석을 아끼지 않는다. 다만 **무엇을 하는지가 아니라 왜 그런지**를 적는다.

```java
// saveAndFlush: INSERT 를 이 메서드 안에서 터뜨려야 uk_users_name_tag 경합이
// pending 세션 삭제보다 먼저 드러난다. 커밋 시점 flush 면 세션을 이미 지운 뒤
// 실패해 사용자의 토큰이 죽는다.
```

- **한글로 쓴다** (Javadoc 포함)
- 대안을 기각한 이유를 남긴다 (`String.hashCode()` 는 32bit 라…)
- 이슈 번호를 남길 때가 있다 (`이슈 #25`)
- "현재는 검증 없이 통과시킨다" 처럼 **미완성 상태를 명시**한다

---

## 9. 빌드

| | |
|---|---|
| Java | 21 (toolchain) |
| Spring Boot | 4.0.3 / Spring gRPC 1.0.2 |
| Lombok | `@Getter`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j` 사용. `@Data` 는 쓰지 않는다 |
| proto | `sourceSets.main.proto.srcDirs = ['.../external', '.../internal']` |
| 테스트 | JUnit 5 (`useJUnitPlatform()`) |

- proto 생성 코드에 `option '@generated=omit'` 이 붙어 있다
- ⚠️ `jacoco` 플러그인이 선언되어 있지만 **리포트 태스크가 `test` 에 연결되어 있지 않다.**
  커버리지를 보려면 설정을 추가해야 한다

---

## 10. 함께 볼 문서

- [grpc-contract.md](grpc-contract.md) — 레이어 규칙의 프로토콜 쪽 근거
- [error-catalog.md](error-catalog.md) — 예외 → status 매핑
- [system-architecture.md](../01-foundation/system-architecture.md) — 패키지 구조의 배경
