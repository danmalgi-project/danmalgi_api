#!/usr/bin/env bash
#
# PR 생성 스크립트.
#
# 현재 브랜치를 푸시하고 PR 을 만든다. 제목과 본문은 커밋에서 뽑아 초안을 채운다.
#
# 올리기 전에 확인하는 것:
#   - main 브랜치가 아닌지 (main 에서 PR 을 만들 수는 없다)
#   - 커밋되지 않은 변경이 남아 있지 않은지
#   - base 와 비교해 올릴 커밋이 실제로 있는지
#   - 이미 열린 PR 이 있는지 (있으면 URL 만 알려주고 끝낸다)
#
# 원격에 푸시하는 동작이라 기본적으로 한 번 확인을 받는다. --yes 로 건너뛴다.
# 비대화 환경(파이프, CI)에서는 --yes 가 없으면 진행하지 않는다.
#
# 사용법:
#   scripts/pr.sh                       # 커밋에서 제목/본문을 뽑아 PR 생성
#   scripts/pr.sh --dry-run             # 만들지 않고 무엇을 올릴지만 출력
#   scripts/pr.sh --title "feat: ..."   # 제목 지정
#   scripts/pr.sh --body-file notes.md  # 본문을 파일에서
#   scripts/pr.sh --issue 12            # 본문에 "Closes #12" 추가
#   scripts/pr.sh --draft               # 초안으로
#   scripts/pr.sh --base develop        # 기본 base 는 main
#   scripts/pr.sh --verify              # 올리기 전에 scripts/verify.sh --changed
#   scripts/pr.sh --claude              # 본문 끝에 Claude Code 생성 표기
#   scripts/pr.sh --yes                 # 확인 프롬프트 생략
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

command -v gh >/dev/null || { echo "error: gh CLI 가 필요합니다. https://cli.github.com" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "error: gh 인증이 안 돼 있습니다. 'gh auth login' 을 먼저 실행하세요." >&2; exit 1; }

BASE=main
TITLE=""
BODY=""
BODY_FILE=""
ISSUE=""
DRAFT=0
DRY_RUN=0
VERIFY=0
CLAUDE=0
ASSUME_YES=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base)       BASE="${2:?--base 에 값이 필요합니다}"; shift 2 ;;
    --base=*)     BASE="${1#*=}"; shift ;;
    --title)      TITLE="${2:?--title 에 값이 필요합니다}"; shift 2 ;;
    --title=*)    TITLE="${1#*=}"; shift ;;
    --body)       BODY="${2:?--body 에 값이 필요합니다}"; shift 2 ;;
    --body-file)  BODY_FILE="${2:?--body-file 에 값이 필요합니다}"
                  [[ -f "$BODY_FILE" ]] || { echo "error: $BODY_FILE 이 없습니다." >&2; exit 1; }
                  shift 2 ;;
    --issue)      ISSUE="${2:?--issue 에 값이 필요합니다}"; shift 2 ;;
    --issue=*)    ISSUE="${1#*=}"; shift ;;
    --draft)      DRAFT=1; shift ;;
    --dry-run)    DRY_RUN=1; shift ;;
    --verify)     VERIFY=1; shift ;;
    --claude)     CLAUDE=1; shift ;;
    -y|--yes)     ASSUME_YES=1; shift ;;
    -h|--help)    awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)            echo "error: 알 수 없는 인자 '$1'" >&2; exit 1 ;;
  esac
done

BRANCH="$(git branch --show-current)"

# ---------------------------------------------------------------- 사전 확인
[[ -n "$BRANCH" ]] || { echo "error: detached HEAD 상태입니다. 브랜치를 체크아웃하세요." >&2; exit 1; }

if [[ "$BRANCH" == "$BASE" ]]; then
  echo "error: 현재 브랜치가 base($BASE) 와 같습니다. 작업 브랜치를 만들고 다시 실행하세요." >&2
  echo "       git switch -c feat/<이름>" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "error: 커밋되지 않은 변경이 있습니다. 커밋하거나 stash 한 뒤 실행하세요." >&2
  git status --short >&2
  exit 1
fi

# base 를 원격 기준으로 맞춰 둔다. 로컬 main 이 낡았으면 커밋 목록이 엉뚱해진다.
git fetch --quiet origin "$BASE" 2>/dev/null || true
BASE_REF="origin/$BASE"
git rev-parse --verify --quiet "$BASE_REF" >/dev/null || BASE_REF="$BASE"

COMMITS="$(git log --oneline "$BASE_REF..HEAD" 2>/dev/null || true)"
if [[ -z "$COMMITS" ]]; then
  echo "error: $BASE_REF 대비 올릴 커밋이 없습니다." >&2
  exit 1
fi
COMMIT_COUNT="$(wc -l <<<"$COMMITS")"

EXISTING="$(gh pr list --head "$BRANCH" --state open --json url --jq '.[0].url' 2>/dev/null || true)"
if [[ -n "$EXISTING" ]]; then
  echo "이미 열린 PR 이 있습니다: $EXISTING"
  echo "브랜치를 푸시하면 그 PR 에 반영됩니다: git push"
  exit 0
fi

# ---------------------------------------------------------------- 제목/본문
if [[ -z "$TITLE" ]]; then
  if [[ "$COMMIT_COUNT" -eq 1 ]]; then
    TITLE="$(git log -1 --pretty=%s)"
  else
    # 여러 커밋이면 브랜치 이름에서 뽑는다. feat/harness-setup -> feat: harness setup
    TITLE="$(sed -E 's|^([a-z]+)/|\1: |; s|-| |g' <<<"$BRANCH")"
  fi
fi

if [[ -n "$BODY_FILE" ]]; then
  BODY="$(cat "$BODY_FILE")"
elif [[ -z "$BODY" ]]; then
  BODY="$(printf '## 변경 내용\n\n%s\n' "$(git log --pretty='- %s' "$BASE_REF..HEAD")")"
  BODY+=$'\n\n## 검증\n\n- [ ] `scripts/verify.sh --changed`\n'
fi

[[ -n "$ISSUE" ]] && BODY+=$'\n\nCloses #'"$ISSUE"$'\n'
[[ $CLAUDE -eq 1 ]] && BODY+=$'\n🤖 Generated with [Claude Code](https://claude.com/claude-code)\n'

# ---------------------------------------------------------------- 검증
if [[ $VERIFY -eq 1 ]]; then
  echo "==> 올리기 전 검증"
  if ! ./scripts/verify.sh --base "$BASE_REF"; then
    echo "" >&2
    echo "error: 검증에 실패했습니다. 고치고 다시 실행하거나, --verify 없이 실행하세요." >&2
    exit 1
  fi
  echo ""
fi

# ---------------------------------------------------------------- 요약
cat <<SUMMARY
==> 올릴 내용
  브랜치   $BRANCH -> $BASE
  제목     $TITLE
  커밋     ${COMMIT_COUNT}개
$(sed 's|^|    |' <<<"$COMMITS")
  초안     $([[ $DRAFT -eq 1 ]] && echo 예 || echo 아니오)

--- 본문 ---
$BODY
-------------
SUMMARY

if [[ $DRY_RUN -eq 1 ]]; then
  echo ""
  echo "(--dry-run 이라 실제로 푸시하거나 PR 을 만들지 않았습니다)"
  exit 0
fi

# ---------------------------------------------------------------- 확인
if [[ $ASSUME_YES -eq 0 ]]; then
  if [[ ! -t 0 ]]; then
    echo "" >&2
    echo "error: 확인을 받을 수 없는 환경입니다. 내용을 확인했다면 --yes 를 붙여 실행하세요." >&2
    exit 1
  fi
  read -r -p $'\n푸시하고 PR 을 만들까요? [y/N] ' answer
  [[ "$answer" =~ ^[Yy]$ ]] || { echo "취소했습니다."; exit 1; }
fi

# ---------------------------------------------------------------- 실행
echo ""
echo "==> git push -u origin $BRANCH"
git push -u origin "$BRANCH"

GH_ARGS=(--base "$BASE" --head "$BRANCH" --title "$TITLE" --body "$BODY")
[[ $DRAFT -eq 1 ]] && GH_ARGS+=(--draft)

echo "==> gh pr create"
gh pr create "${GH_ARGS[@]}"
