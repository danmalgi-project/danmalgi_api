#!/usr/bin/env bash
#
# GitHub 이슈 조회 스크립트.
#
# 기본 동작: 열린 이슈를 한 줄씩 보여준다.
# 번호를 주면 그 이슈의 본문과 코멘트까지 펼친다.
#
# 사용법:
#   scripts/issues.sh                   # 열린 이슈
#   scripts/issues.sh 12                # 12번 이슈 상세 (본문 + 코멘트)
#   scripts/issues.sh --mine            # 나에게 할당된 것만
#   scripts/issues.sh --all             # 닫힌 것까지
#   scripts/issues.sh --label bug       # 라벨 필터 (반복 가능)
#   scripts/issues.sh --search "토큰"    # 제목/본문 검색
#   scripts/issues.sh --limit 50        # 기본 30
#   scripts/issues.sh --json            # 가공 없이 JSON
#   scripts/issues.sh --repo owner/name # 다른 리포 (예: proto 서브모듈)
#   scripts/issues.sh --web             # 브라우저로
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

command -v gh >/dev/null || { echo "error: gh CLI 가 필요합니다. https://cli.github.com" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "error: gh 인증이 안 돼 있습니다. 'gh auth login' 을 먼저 실행하세요." >&2; exit 1; }

STATE=open
LIMIT=30
JSON=0
WEB=0
NUMBER=""
SEARCH=""
GH_ARGS=()
REPO_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mine)     GH_ARGS+=(--assignee "@me"); shift ;;
    -R|--repo)  REPO_ARGS=(--repo "${2:?--repo 에 값이 필요합니다}"); shift 2 ;;
    --repo=*)   REPO_ARGS=(--repo "${1#*=}"); shift ;;
    --all)      STATE=all; shift ;;
    --closed)   STATE=closed; shift ;;
    --label)    GH_ARGS+=(--label "${2:?--label 에 값이 필요합니다}"); shift 2 ;;
    --label=*)  GH_ARGS+=(--label "${1#*=}"); shift ;;
    --author)   GH_ARGS+=(--author "${2:?--author 에 값이 필요합니다}"); shift 2 ;;
    --search)   SEARCH="${2:?--search 에 값이 필요합니다}"; shift 2 ;;
    --search=*) SEARCH="${1#*=}"; shift ;;
    --limit)    LIMIT="${2:?--limit 에 값이 필요합니다}"; shift 2 ;;
    --limit=*)  LIMIT="${1#*=}"; shift ;;
    --json)     JSON=1; shift ;;
    --web)      WEB=1; shift ;;
    -h|--help)  awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    [0-9]*)     NUMBER="$1"; shift ;;
    *)          echo "error: 알 수 없는 인자 '$1'" >&2; exit 1 ;;
  esac
done

# ---------------------------------------------------------------- 이슈 하나
if [[ -n "$NUMBER" ]]; then
  [[ $WEB -eq 1 ]] && exec gh issue view "$NUMBER" ${REPO_ARGS[@]+"${REPO_ARGS[@]}"} --web
  [[ $JSON -eq 1 ]] && exec gh issue view "$NUMBER" ${REPO_ARGS[@]+"${REPO_ARGS[@]}"} --json number,title,state,body,labels,assignees,comments,url,createdAt,updatedAt
  gh issue view "$NUMBER" ${REPO_ARGS[@]+"${REPO_ARGS[@]}"} --comments
  exit 0
fi

# ---------------------------------------------------------------- 목록
[[ $WEB -eq 1 ]] && exec gh issue list ${REPO_ARGS[@]+"${REPO_ARGS[@]}"} --state "$STATE" --web

[[ -n "$SEARCH" ]] && GH_ARGS+=(--search "$SEARCH")

if [[ $JSON -eq 1 ]]; then
  exec gh issue list ${REPO_ARGS[@]+"${REPO_ARGS[@]}"} --state "$STATE" --limit "$LIMIT" ${GH_ARGS[@]+"${GH_ARGS[@]}"} \
    --json number,title,state,labels,assignees,url,createdAt,updatedAt
fi

raw="$(gh issue list ${REPO_ARGS[@]+"${REPO_ARGS[@]}"} --state "$STATE" --limit "$LIMIT" ${GH_ARGS[@]+"${GH_ARGS[@]}"} \
  --json number,title,state,labels,assignees,updatedAt)"

python3 - "$raw" <<'PYEOF'
import json, sys

issues = json.loads(sys.argv[1])
if not issues:
    print("이슈가 없습니다.")
    sys.exit(0)

def join(items, key):
    return ",".join(i[key] for i in items) or "-"

width = max(len(str(i["number"])) for i in issues)
for it in issues:
    labels = join(it["labels"], "name")
    who = join(it["assignees"], "login")
    state = it["state"].lower()
    mark = "●" if state == "open" else "○"   # 열림/닫힘을 한 글자로
    print(f"{mark} #{str(it['number']).rjust(width)}  {it['title']}")
    print(f"  {' ' * width}  {it['updatedAt'][:10]}  담당 {who}  라벨 {labels}")

print(f"\n총 {len(issues)}건")
PYEOF
