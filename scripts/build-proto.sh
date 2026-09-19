#!/usr/bin/env bash
#
# proto 코드 생성 스크립트.
#
# 기본 동작: proto 서브모듈이 비어 있으면 체크아웃한 뒤 generateProto 를 돌린다.
#           (build/generated/sources/proto 아래에 java / grpc 스텁이 생성된다)
#
# 사용법:
#   scripts/build-proto.sh              # 서브모듈 확인 + proto 생성
#   scripts/build-proto.sh --update     # 서브모듈을 원격 최신으로 갱신한 뒤 생성
#   scripts/build-proto.sh --clean      # 기존 생성물을 지우고 다시 생성
#   scripts/build-proto.sh --test       # 테스트용 proto(generateTestProto)도 함께 생성
#   scripts/build-proto.sh -- --info    # -- 뒤의 인자는 gradle 에 그대로 전달
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PROTO_DIRS=(src/main/proto/external src/main/proto/internal)
GENERATED_DIR=build/generated/sources/proto

DO_UPDATE=0
DO_CLEAN=0
WITH_TEST=0
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --update)  DO_UPDATE=1; shift ;;
    --clean)   DO_CLEAN=1; shift ;;
    --test)    WITH_TEST=1; shift ;;
    --)        shift; GRADLE_ARGS+=("$@"); break ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)         GRADLE_ARGS+=("$1"); shift ;;
  esac
done

if [[ $DO_UPDATE -eq 1 ]]; then
  echo "==> proto 서브모듈을 원격 최신으로 갱신"
  git submodule update --init --remote --recursive "${PROTO_DIRS[@]}"
else
  # 아직 체크아웃되지 않은 서브모듈만 받아온다. (커밋 고정본 유지)
  for proto_dir in "${PROTO_DIRS[@]}"; do
    if [[ -z "$(ls -A "$proto_dir" 2>/dev/null)" ]]; then
      echo "==> $proto_dir 가 비어 있어 체크아웃합니다"
      git submodule update --init --recursive "$proto_dir"
    fi
  done
fi

for proto_dir in "${PROTO_DIRS[@]}"; do
  if [[ -z "$(ls -A "$proto_dir" 2>/dev/null)" ]]; then
    echo "error: $proto_dir 체크아웃에 실패했습니다. 서브모듈 접근 권한을 확인하세요." >&2
    exit 1
  fi
done

# 어떤 커밋의 proto 로 생성하는지 남겨둔다.
echo "==> proto 소스"
git submodule status "${PROTO_DIRS[@]}" | sed 's/^/    /'

if [[ $DO_CLEAN -eq 1 ]]; then
  echo "==> 기존 생성물 삭제: $GENERATED_DIR"
  rm -rf "$GENERATED_DIR"
fi

CMD=(generateProto)
[[ $WITH_TEST -eq 1 ]] && CMD+=(generateTestProto)
CMD+=(${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"})

echo "==> ./gradlew ${CMD[*]}"
./gradlew "${CMD[@]}"

echo ""
echo "생성 완료: $GENERATED_DIR"
if [[ -d "$GENERATED_DIR" ]]; then
  find "$GENERATED_DIR" -name '*.java' | wc -l | xargs printf "    java 파일 %s개\n"
fi
