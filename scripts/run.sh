#!/usr/bin/env bash
#
# 앱 로컬 실행 스크립트. (Spring Boot gRPC 서버)
#
# 기동 전에 로컬 설정과 필수 환경변수를 먼저 확인한다.
# 리포지토리 루트에 .env 가 있으면 읽어서 환경변수로 넣는다.
#
# 사용법:
#   scripts/run.sh                      # bootRun
#   scripts/run.sh --profile dev        # spring profile 지정
#   scripts/run.sh --port 9090          # gRPC 서버 포트 오버라이드
#   scripts/run.sh --debug              # JVM 디버거 대기 (5005)
#   scripts/run.sh --build              # 기동 전에 clean build 수행
#   scripts/run.sh -- --info            # -- 뒤의 인자는 gradle 에 그대로 전달
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PROFILE=""
PORT=""
DEBUG=0
DO_BUILD=0
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="${2:?--profile 에 값이 필요합니다}"; shift 2 ;;
    --profile=*) PROFILE="${1#*=}"; shift ;;
    --port)    PORT="${2:?--port 에 값이 필요합니다}"; shift 2 ;;
    --port=*)  PORT="${1#*=}"; shift ;;
    --debug)   DEBUG=1; shift ;;
    --build)   DO_BUILD=1; shift ;;
    --)        shift; GRADLE_ARGS+=("$@"); break ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)         GRADLE_ARGS+=("$1"); shift ;;
  esac
done

# .env 가 있으면 먼저 반영한다. 이미 셸에 있는 값이 우선한다.
if [[ -f .env ]]; then
  echo "==> .env 로드"
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

# application.yaml 은 비밀값을 담고 있어 커밋되지 않는다. 없으면 예시 파일에서 시작해야 한다.
if [[ ! -f src/main/resources/application.yaml ]]; then
  echo "error: src/main/resources/application.yaml 이 없습니다." >&2
  echo "       cp src/main/resources/application-example.yaml src/main/resources/application.yaml 로 만든 뒤 값을 채우세요." >&2
  exit 1
fi

# 기본값이 없어 미주입 시 기동 중 실패하는 값들을 미리 잡는다.
if [[ -z "${R2_PUBLIC_BASE_URL:-}" ]]; then
  echo "error: R2_PUBLIC_BASE_URL 이 설정되지 않았습니다. (application.yaml 의 r2.public-base-url 에 필요)" >&2
  echo "       export R2_PUBLIC_BASE_URL=... 하거나 .env 에 추가하세요." >&2
  exit 1
fi

for proto_dir in src/main/proto/external src/main/proto/internal; do
  if [[ -z "$(ls -A "$proto_dir" 2>/dev/null)" ]]; then
    echo "error: $proto_dir 가 비어 있습니다. 먼저 'git submodule update --init --recursive' 를 실행하세요." >&2
    exit 1
  fi
done

if [[ $DO_BUILD -eq 1 ]]; then
  "$REPO_ROOT/scripts/build.sh"
fi

# profile/port 는 스프링 애플리케이션 인자로 넘긴다.
APP_ARGS=()
[[ -n "$PROFILE" ]] && APP_ARGS+=("--spring.profiles.active=$PROFILE")
[[ -n "$PORT" ]] && APP_ARGS+=("--spring.grpc.server.port=$PORT")

CMD=(bootRun)
[[ ${#APP_ARGS[@]} -gt 0 ]] && CMD+=("--args=${APP_ARGS[*]}")
[[ $DEBUG -eq 1 ]] && CMD+=(--debug-jvm)
CMD+=(${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"})

[[ $DEBUG -eq 1 ]] && echo "==> 디버거 연결 대기: localhost:5005"
echo "==> ./gradlew ${CMD[*]}"
exec ./gradlew "${CMD[@]}"
