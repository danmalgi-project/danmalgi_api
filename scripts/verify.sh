#!/usr/bin/env bash
#
# 검증 게이트. proto 생성 -> 빌드 -> 테스트 동기화 -> 단위 테스트를 순서대로 돌리고
# 마지막에 단계별 결과를 한 번에 보여준다.
#
# 각 단계는 기존 스크립트를 그대로 호출한다. (로직을 여기 중복해서 두지 않는다)
#   proto  -> scripts/build-proto.sh
#   build  -> scripts/build.sh --no-clean
#   sync   -> scripts/check-test-sync.py
#   test   -> scripts/test-unit.sh
#
# 단계가 실패해도 뒤 단계를 계속 돌린다. 한 번 실행으로 문제를 모두 보기 위해서다.
# (빌드가 깨지면 테스트는 의미가 없으므로 그때만 건너뛴다)
#
# 사용법:
#   scripts/verify.sh                  # 전체 게이트
#   scripts/verify.sh --changed        # 변경된 파일에 대응하는 테스트만 실행
#   scripts/verify.sh --base main      # --changed 의 비교 기준을 main 으로
#   scripts/verify.sh --skip-proto --skip-build   # 단계 생략
#   scripts/verify.sh --coverage       # jacoco 리포트까지
#   scripts/verify.sh -- --info        # -- 뒤의 인자는 gradle 에 그대로 전달
#
# 종료 코드: 0 전부 통과 / 1 실패한 단계 있음
set -uo pipefail   # -e 는 쓰지 않는다. 단계 실패를 직접 수집해야 한다.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CHANGED=0
BASE=""
STRICT_SYNC=0
COVERAGE=0
SKIP_PROTO=0; SKIP_BUILD=0; SKIP_SYNC=0; SKIP_TESTS=0
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --changed)     CHANGED=1; shift ;;
    --base)        BASE="${2:?--base 에 값이 필요합니다}"; CHANGED=1; shift 2 ;;
    --base=*)      BASE="${1#*=}"; CHANGED=1; shift ;;
    --coverage)    COVERAGE=1; shift ;;
    --strict-sync) STRICT_SYNC=1; shift ;;
    --skip-proto)  SKIP_PROTO=1; shift ;;
    --skip-build)  SKIP_BUILD=1; shift ;;
    --skip-sync)   SKIP_SYNC=1; shift ;;
    --skip-tests)  SKIP_TESTS=1; shift ;;
    --)            shift; GRADLE_ARGS+=("$@"); break ;;
    -h|--help)     awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)             echo "error: 알 수 없는 인자 '$1'" >&2; exit 1 ;;
  esac
done

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  C_BOLD=$'\033[1m'; C_OFF=$'\033[0m'
  C_PASS=$'\033[32m'; C_FAIL=$'\033[31m'; C_WARN=$'\033[33m'; C_DIM=$'\033[2m'
else
  C_BOLD=""; C_OFF=""; C_PASS=""; C_FAIL=""; C_WARN=""; C_DIM=""
fi

STEP_NAMES=(); STEP_STATUS=(); STEP_NOTE=()

record() { STEP_NAMES+=("$1"); STEP_STATUS+=("$2"); STEP_NOTE+=("${3:-}"); }

banner() { printf '\n%s==> %s%s\n' "$C_BOLD" "$*" "$C_OFF"; }

# 변경/추가된 .java 파일 목록. (--changed 계열이 공통으로 쓴다)
changed_java_files() {
  local files
  if [[ -n "$BASE" ]]; then
    files="$(git diff --name-only "$BASE"...HEAD 2>/dev/null; git diff --name-only HEAD 2>/dev/null)"
  else
    files="$(git status --porcelain 2>/dev/null | sed 's/^...//')"
  fi
  while IFS= read -r file; do
    [[ "$file" == *.java && -f "$file" ]] && echo "$file"
  done <<<"$files" | sort -u
}

# 변경된 .java 에 대응하는 테스트 클래스 이름을 뽑는다.
#   테스트 파일을 고쳤으면 그 클래스, 소스를 고쳤으면 같은 패키지의 <Class>*Test 전부.
changed_test_classes() {
  local files
  if [[ -n "$BASE" ]]; then
    files="$(git diff --name-only "$BASE"...HEAD 2>/dev/null; git diff --name-only HEAD 2>/dev/null)"
  else
    # 스테이징/워킹트리/미추적을 모두 본다.
    files="$(git status --porcelain 2>/dev/null | sed 's/^...//')"
  fi

  local out=()
  while IFS= read -r file; do
    [[ "$file" == *.java ]] || continue
    if [[ "$file" == src/test/java/* ]]; then
      [[ -f "$file" ]] && out+=("$(basename "$file" .java)")
    elif [[ "$file" == src/main/java/* ]]; then
      local pkg_dir test_dir stem
      stem="$(basename "$file" .java)"
      pkg_dir="$(dirname "${file#src/main/java/}")"
      test_dir="src/test/java/$pkg_dir"
      [[ -d "$test_dir" ]] || continue
      while IFS= read -r t; do
        [[ -n "$t" ]] && out+=("$(basename "$t" .java)")
      done < <(find "$test_dir" -maxdepth 1 -name "${stem}*Test.java" 2>/dev/null)
    fi
  done <<<"$files"

  printf '%s\n' "${out[@]+"${out[@]}"}" | sort -u | sed '/^$/d'
}

# ---------------------------------------------------------------- 1. proto
if [[ $SKIP_PROTO -eq 1 ]]; then
  record proto SKIP
else
  banner "proto 생성"
  if ./scripts/build-proto.sh; then record proto PASS; else record proto FAIL "scripts/build-proto.sh"; fi
fi

# ---------------------------------------------------------------- 2. build
BUILD_OK=1
if [[ $SKIP_BUILD -eq 1 ]]; then
  record build SKIP
else
  banner "빌드"
  if ./scripts/build.sh --no-clean ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}; then
    record build PASS
  else
    record build FAIL "scripts/build.sh --no-clean"
    BUILD_OK=0
  fi
fi

# ---------------------------------------------------------------- 3. 테스트 동기화
if [[ $SKIP_SYNC -eq 1 ]]; then
  record sync SKIP
else
  # --changed 면 변경된 소스만 본다. 그래야 남의 기존 부채로 이번 변경이 막히지 않는다.
  SYNC_TARGETS=()
  if [[ $CHANGED -eq 1 ]]; then
    mapfile -t SYNC_TARGETS < <(changed_java_files)
  fi

  banner "테스트 동기화 검사"
  if [[ $CHANGED -eq 1 && ${#SYNC_TARGETS[@]} -eq 0 ]]; then
    echo "변경된 자바 소스가 없습니다."
    record sync SKIP "변경된 자바 소스 없음"
  elif ./scripts/check-test-sync.py ${SYNC_TARGETS[@]+"${SYNC_TARGETS[@]}"} </dev/null; then
    record sync PASS
  elif [[ $CHANGED -eq 1 || $STRICT_SYNC -eq 1 ]]; then
    record sync FAIL "scripts/check-test-sync.py"
  else
    # 리포 전체 검사는 이번 변경과 무관한 기존 누락까지 잡는다. 보고는 하되 막지는 않는다.
    record sync WARN "기존 누락 포함 - 막지 않음 (--strict-sync 로 차단)"
  fi
fi

# ---------------------------------------------------------------- 4. 단위 테스트
if [[ $SKIP_TESTS -eq 1 ]]; then
  record test SKIP
elif [[ $BUILD_OK -eq 0 ]]; then
  record test SKIP "빌드 실패로 생략"
else
  TEST_ARGS=()
  [[ $COVERAGE -eq 1 ]] && TEST_ARGS+=(--coverage)

  if [[ $CHANGED -eq 1 ]]; then
    mapfile -t CLASSES < <(changed_test_classes)
    if [[ ${#CLASSES[@]} -eq 0 ]]; then
      banner "단위 테스트 (변경분)"
      echo "변경된 파일에 대응하는 테스트 클래스가 없습니다."
      record test SKIP "변경분에 대응하는 테스트 없음"
    else
      banner "단위 테스트 (변경분 ${#CLASSES[@]}개)"
      printf '    %s\n' "${CLASSES[@]}"
      if ./scripts/test-unit.sh "${CLASSES[@]}" ${TEST_ARGS[@]+"${TEST_ARGS[@]}"}; then
        record test PASS "${#CLASSES[@]}개 클래스"
      else
        record test FAIL "build/reports/tests/test/index.html"
      fi
    fi
  else
    banner "단위 테스트 (전체)"
    if ./scripts/test-unit.sh ${TEST_ARGS[@]+"${TEST_ARGS[@]}"}; then
      record test PASS
    else
      record test FAIL "build/reports/tests/test/index.html"
    fi
  fi
fi

# ---------------------------------------------------------------- 요약
printf '\n%s==> 결과%s\n' "$C_BOLD" "$C_OFF"
failed=0
for i in "${!STEP_NAMES[@]}"; do
  case "${STEP_STATUS[$i]}" in
    PASS) mark="${C_PASS}PASS${C_OFF}" ;;
    FAIL) mark="${C_FAIL}FAIL${C_OFF}"; failed=1 ;;
    WARN) mark="${C_WARN}WARN${C_OFF}" ;;
    *)    mark="${C_DIM}SKIP${C_OFF}" ;;
  esac
  printf '  [%s] %-6s %s\n' "$mark" "${STEP_NAMES[$i]}" "${STEP_NOTE[$i]}"
done

passed=0
for status in ${STEP_STATUS[@]+"${STEP_STATUS[@]}"}; do
  [[ "$status" == PASS ]] && passed=$((passed + 1))
done

echo ""
if [[ $failed -ne 0 ]]; then
  echo "실패한 단계가 있습니다."
elif [[ $passed -eq 0 ]]; then
  # 전부 생략된 것을 "통과"라고 부르면 안 된다. 검증한 게 없다는 뜻이다.
  echo "검증된 단계가 없습니다. (전부 생략됨)"
else
  echo "통과 ($passed 단계)"
fi
exit $failed
