#!/usr/bin/env bash
#
# Gradle 빌드 스크립트.
#
# 기본 동작: clean 후 테스트를 제외하고 빌드한다. (컴파일 + proto 생성 + 패키징 검증)
# 테스트까지 함께 돌리려면 --with-tests 를 준다.
#
# 사용법:
#   scripts/build.sh                    # clean build -x test
#   scripts/build.sh --with-tests       # clean build (테스트 포함)
#   scripts/build.sh --no-clean         # clean 생략
#   scripts/build.sh -- --info          # -- 뒤의 인자는 gradle 에 그대로 전달
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

WITH_TESTS=0
DO_CLEAN=1
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-tests) WITH_TESTS=1; shift ;;
    --no-clean)   DO_CLEAN=0; shift ;;
    --)           shift; GRADLE_ARGS+=("$@"); break ;;
    -h|--help)    awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)            GRADLE_ARGS+=("$1"); shift ;;
  esac
done

# proto 서브모듈이 체크아웃되어 있지 않으면 protobuf 생성 단계에서 불친절하게 깨진다.
for proto_dir in src/main/proto/external src/main/proto/internal; do
  if [[ -z "$(ls -A "$proto_dir" 2>/dev/null)" ]]; then
    echo "error: $proto_dir 가 비어 있습니다. 먼저 'git submodule update --init --recursive' 를 실행하세요." >&2
    exit 1
  fi
done

TASKS=()
[[ $DO_CLEAN -eq 1 ]] && TASKS+=(clean)
TASKS+=(build)
[[ $WITH_TESTS -eq 0 ]] && GRADLE_ARGS+=(-x test)

echo "==> ./gradlew ${TASKS[*]} ${GRADLE_ARGS[*]}"
exec ./gradlew "${TASKS[@]}" "${GRADLE_ARGS[@]}"
