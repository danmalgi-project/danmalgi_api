# danmalgi_api

Spring Boot 4 + Spring gRPC 기반 백엔드 서버. Java 21 / PostgreSQL / Redis / Cloudflare R2.
`danmalgi_chat`, `danmalgi_webrtc` 와 함께 도는 3개 서버 중 하나다.

이 파일은 **짧게 유지한다.** 자세한 내용은 `docs/` 에 있고, 여기에는 포인터와
반드시 지켜야 할 규칙만 둔다.

---

## 작업 시작 전에 읽을 것

| 상황 | 문서 |
|---|---|
| 처음 이 리포를 만짐 | [docs/README.md](docs/README.md) — 읽는 순서가 적혀 있다 |
| 코드를 쓰기 전 | [docs/04-conventions/code-conventions.md](docs/04-conventions/code-conventions.md) |
| RPC 를 추가·수정 | [docs/04-conventions/grpc-contract.md](docs/04-conventions/grpc-contract.md) |
| 예외를 던질 때 | [docs/04-conventions/error-catalog.md](docs/04-conventions/error-catalog.md) |

용어(`Relation` vs `Friendship`, `DMC` vs `UDMC`)를 모르면
[domain-glossary.md](docs/01-foundation/domain-glossary.md) 부터 본다.

---

## 작업 흐름

코드를 **바꾸는** 요청은 `dev-flow` 스킬을 쓴다. plan → work → review 순서이고
단계를 건너뛰지 않는다. (오타·상수 하나 같은 자명한 수정은 예외 — 고치고
`scripts/verify.sh --changed` 만 돌린다)

- 분석·계획: `planner` 에이전트 (코드 수정 안 함)
- 빌드·테스트 검증: `reviewer` 에이전트 (코드 수정 안 함)
- 구현은 에이전트에 넘기지 않고 직접 한다

코드를 찾을 때는 맨손 `rg` 보다 `scripts/find-code.sh` 가 먼저다.
레이어 구조와 테스트 컨벤션을 이미 아는 질의들이 들어 있다.

---

## 명령

| 명령 | 용도 |
|---|---|
| `scripts/verify.sh --changed` | **기본 검증** — proto → build → test-sync → test |
| `scripts/verify.sh` | 전체 게이트 |
| `scripts/build.sh` | clean build (`-x test`) |
| `scripts/test-unit.sh [패턴]` | 단위 테스트 |
| `scripts/build-proto.sh` | proto 서브모듈 체크아웃 + 코드 생성 |
| `scripts/run.sh` | 로컬 기동 (bootRun) |
| `scripts/find-code.sh <slice\|rpc\|api\|who\|layers>` | 코드 구조 질의 |
| `scripts/check-grpc.py` | 서버가 떠 있을 때 RPC 도달 확인 |
| `scripts/issues.sh`, `scripts/pr.sh` | GitHub 이슈 / PR |

각 스크립트는 `--help` 로 사용법을 낸다. **SKIP 은 통과가 아니다.**
돌리지 않은 검사를 돌렸다고 보고하지 않는다.

---

## 손대면 안 되는 것

- `src/main/proto/**` — **git 서브모듈**이다. 이 리포에서 수정할 수 없다.
  스키마 변경이 필요하면 구현을 멈추고 사용자에게 알린다.
- `build/generated/**` — proto 생성 코드.
- `src/main/resources/application.yaml` — `.gitignore` 대상이고 **평문 시크릿**이 들어 있다.
  커밋하지 않는다. 설정 예시는 `application-example.yaml` 에 키 이름만 추가한다.
- 문서·커밋·코드 어디에도 시크릿 값을 쓰지 않는다. 키 이름까지만.

---

## 코드 규칙 (요약 — 근거는 code-conventions.md)

- 패키지는 **도메인 우선, 그 안을 레이어로**. (`user/grpc`, `user/service`, …)
- 요청 흐름: gRPC 핸들러 → validator → service → mapper. 구현도 이 순서로 한다.
- proto 타입과 `StreamObserver` 는 `grpc/` 패키지를 넘지 않는다.
- 엔티티에 `@Setter` 를 쓰지 않는다. 정적 팩토리 + 의도가 드러나는 `updateXxx()`.
- validator 는 `IllegalArgumentException` 만, 형식 검사만. 존재·권한은 service 의 도메인 예외.
- 도메인 예외는 `GrpcException` 을 구현해 status 를 스스로 선언한다.
- 주석은 **한글**로, 무엇이 아니라 **왜**를 적는다. 미완성 상태는 명시한다.

### 테스트는 구현과 같이 쓴다

메서드 하나당 테스트 클래스 하나, 소스와 **같은 패키지**, 이름은
`<Class><MethodPascal>Test` (mapper/validator 는 접두어 없는 `<MethodPascal>Test` 도 쓴다).
테스트 메서드명은 한글로 `{메서드}_{상황}_{기대}`. 단언은 AssertJ.
`scripts/check-test-sync.py` 가 이 규칙을 검사한다 — **기존 누락 13건**은
이번 변경 것이 아니니 섞어서 보고하지 않는다.

테스트가 깨지면 **원인을 고친다.** 단언을 약화시키거나 검사를 끄지 않는다.

---

## Git

- **커밋 메시지는 한국어로 쓴다.** 타입 접두어만 영문 conventional 형식.
  `feat: 친구 요청 취소 RPC 추가`, `fix: pending 세션이 먼저 지워지는 문제 수정`,
  `refactor:`, `test:`, `docs:`, `chore:`
- 제목은 한 줄로 무엇을 했는지. 본문이 필요하면 **왜** 그렇게 했는지를 쓴다.
- 브랜치: `main`(기본) / `develop` / 작업은 `feat/*`, `fix/*`. `main` 에 직접 커밋하지 않는다.
- 커밋·푸시는 **사용자가 요청할 때만** 한다.
- PR 은 `scripts/pr.sh` 로 만든다. (`--verify` 로 올리기 전 검증, `--issue N` 으로 Closes 추가)

---

## 문서 유지

동작을 바꾸는 변경은 `docs/` 의 해당 절을 **같이 고친다.**
새 문서는 `docs/README.md` 의 디렉터리 기준표에 따라 넣고 목록에 한 줄 추가한다.
코드를 읽으면 알 수 있는 것은 문서에 쓰지 않는다. **왜 그런지**와 **빈틈**을 쓴다.
