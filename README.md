# danmalgi-backend

## Agent Harness Workflow

- The backend feature-delivery harness now starts by fetching the repository issue list, selecting a GitHub issue, and recording `_workspace/00_input/issue-selection.md` before request analysis begins.
- See [docs/harness/backend-feature-delivery/README.md](docs/harness/backend-feature-delivery/README.md) for the reusable skill stack, skill-as-agent contract, and artifact contract.
- Use the [AGENTS Authoring Guide](.agents/skills/harness/references/agents-md-guide.md) when repo-wide guidance needs to stay short, durable, and pointer-heavy.
- Keep this as a rippable harness: durable contracts live in docs, while evolving retries and heuristics stay easy to remove.
- Generated `SKILL.md` files should start with YAML frontmatter containing at least `name` and `description`.

Spring gRPC 기반 백엔드 서버입니다.

- **Java 21**
- **Spring Boot 4.0.3**
- **Spring gRPC 1.0.2**
- **PostgreSQL** / **Redis** / **Cloudflare R2**

---

## 실행 환경 구성

### PostgreSQL

```bash
docker run -d \
  --name postgres \
  -p 5432:5432 \
  -e POSTGRES_DB=mydb \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_PASSWORD=mypassword \
  postgres:latest
```

### Redis

```bash
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:7-alpine
```

---

## 애플리케이션 빌드 및 실행

### Docker 이미지 빌드

```bash
docker build -t danmalgi-backend:latest .
```

### Docker 컨테이너 실행

```bash
docker run -d \
  --name danmalgi-backend \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<DB_HOST>:5432/mydb \
  -e SPRING_DATASOURCE_USERNAME=myuser \
  -e SPRING_DATASOURCE_PASSWORD=mypassword \
  -e SPRING_DATA_REDIS_HOST=<REDIS_HOST> \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATA_REDIS_PASSWORD=<REDIS_PASSWORD> \
  -e JWT_SECRET=<JWT_SECRET> \
  -e R2_ENDPOINT=<R2_ENDPOINT> \
  -e R2_ACCESS_KEY_ID=<R2_ACCESS_KEY_ID> \
  -e R2_SECRET_ACCESS_KEY=<R2_SECRET_ACCESS_KEY> \
  -e R2_BUCKET=<R2_BUCKET> \
  danmalgi-backend:latest
```

> 로컬에서 Docker로 실행 중인 PostgreSQL/Redis에 접근할 경우 `<DB_HOST>`, `<REDIS_HOST>` 자리에 호스트 머신 IP(예: `host.docker.internal` 또는 `172.29.144.1`)를 입력하세요.

---

## application.yaml 설정 항목

### Spring gRPC

| 키 | 기본값 | 설명 |
|---|---|---|
| `spring.grpc.server.port` | `8080` | gRPC 서버 포트 |
| `spring.grpc.server.reflection.enabled` | `true` | gRPC Reflection 활성화 여부 |

### DataSource (PostgreSQL)

| 키 | 예시값 | 설명 |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/mydb` | DB 접속 URL |
| `spring.datasource.username` | `myuser` | DB 유저명 |
| `spring.datasource.password` | `mypassword` | DB 비밀번호 |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | JDBC 드라이버 |

### JPA

| 키 | 기본값 | 설명 |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `update` | DDL 전략 (`create-drop` / `update` / `validate` / `none`) |
| `spring.jpa.show-sql` | `true` | SQL 로깅 여부 |
| `spring.jpa.properties.hibernate.format_sql` | `true` | SQL 포맷팅 여부 |

### Cache

| 키 | 기본값 | 설명 |
|---|---|---|
| `spring.cache.type` | `redis` | 캐시 타입 |

### Redis

| 키 | 예시값 | 설명 |
|---|---|---|
| `spring.data.redis.host` | `localhost` | Redis 호스트 |
| `spring.data.redis.port` | `6379` | Redis 포트 |
| `spring.data.redis.password` | | Redis 비밀번호 |
| `spring.data.redis.timeout` | `6000` | 연결 타임아웃 (ms) |
| `spring.data.redis.repositories.enabled` | `false` | Spring Data Redis Repository 활성화 여부 |

### Auth Interceptor

| 키 | 기본값 | 설명 |
|---|---|---|
| `auth.interceptor.enable` | `true` | gRPC 인증 인터셉터 활성화 여부 |

### JWT

| 키 | 설명 |
|---|---|
| `jwt.secret` | JWT 서명 시크릿 키 |

### Cloudflare R2

| 키 | 설명 |
|---|---|
| `r2.endpoint` | R2 버킷 엔드포인트 URL |
| `r2.access-key-id` | R2 액세스 키 ID |
| `r2.secret-access-key` | R2 시크릿 액세스 키 |
| `r2.bucket` | R2 버킷 이름 |
