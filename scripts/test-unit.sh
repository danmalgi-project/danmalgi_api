#!/usr/bin/env bash
#
# Gradle 단위 테스트 스크립트.
#
# 기본 동작: 전체 단위 테스트를 실행한다.
# 패턴을 주면 해당 테스트만 실행한다. (gradle --tests 필터)
#
# 사용법:
#   scripts/test-unit.sh                                  # 전체
#   scripts/test-unit.sh UserServiceGetUserTest           # 클래스 하나
#   scripts/test-unit.sh '*DirectMessageService*'         # 와일드카드
#   scripts/test-unit.sh --coverage                       # jacoco 리포트까지 생성
#   scripts/test-unit.sh -- --info                        # -- 뒤의 인자는 gradle 에 그대로 전달
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

COVERAGE=0
FILTERS=()
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --coverage) COVERAGE=1; shift ;;
    --)         shift; GRADLE_ARGS+=("$@"); break ;;
    -h|--help)  awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    -*)         GRADLE_ARGS+=("$1"); shift ;;
    *)          FILTERS+=(--tests "$1"); shift ;;
  esac
done

# --tests 는 바로 앞 태스크에만 적용되므로 test 뒤, 다른 태스크 앞에 와야 한다.
CMD=(test)
CMD+=(${FILTERS[@]+"${FILTERS[@]}"})
[[ $COVERAGE -eq 1 ]] && CMD+=(jacocoTestReport)
CMD+=(${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"})

echo "==> ./gradlew ${CMD[*]}"
set +e
./gradlew "${CMD[@]}"
STATUS=$?
set -e

# 실패했을 때 어느 리포트를 봐야 하는지 짚어준다.
if [[ $STATUS -ne 0 ]]; then
  echo ""
  echo "테스트 실패. 상세 리포트: build/reports/tests/test/index.html" >&2
fi
if [[ $COVERAGE -eq 1 && $STATUS -eq 0 ]]; then
  echo ""
  echo "커버리지 리포트: build/reports/jacoco/test/html/index.html"
fi

exit $STATUS
