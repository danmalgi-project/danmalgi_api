#!/usr/bin/env bash
#
# 코드 구조 질의 스크립트. (설계/분석할 때 파일을 찾아 헤매지 않으려고 만든 것)
#
# rg 는 이미 .gitignore 를 따르므로 단순 필터 래퍼는 의미가 없다. 여기 담은 건
# 이 리포의 레이어 구조(grpc / service / repository / domain)와 테스트 컨벤션을
# 아는 질의들이다.
#
# 사용법:
#   scripts/find-code.sh slice dm            # dm 도메인의 전 레이어 + 테스트 + proto
#   scripts/find-code.sh rpc GetUserByToken  # RPC 하나를 proto->grpc->service->test 로 추적
#   scripts/find-code.sh api UserService     # public 메서드와 각 메서드의 테스트 유무
#   scripts/find-code.sh who JwtTokenProvider # 선언 위치와 참조 위치를 나눠서
#   scripts/find-code.sh layers [도메인]      # 레이어별 파일 수 개요
#
# 그 밖의 검색은 그냥 rg 를 쓰면 된다. (build/ 는 .gitignore 로 이미 빠진다)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

MAIN=src/main/java/com/danmalgi/backend
TEST=src/test/java/com/danmalgi/backend
PROTO=src/main/proto

command -v rg >/dev/null || { echo "error: ripgrep(rg) 이 필요합니다." >&2; exit 1; }

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; }

# 헤더는 stderr 가 아니라 stdout 으로. 파이프로 넘길 때 같이 따라가야 읽힌다.
section() { printf '\n== %s\n' "$*"; }

# 첫 글자만 소문자로. proto 의 RPC 이름(PascalCase)을 자바 메서드명으로 바꾼다.
lower_first() { printf '%s%s' "$(echo "${1:0:1}" | tr 'A-Z' 'a-z')" "${1:1}"; }

cmd_slice() {
    local domain="${1:-}"
    [[ -n "$domain" ]] || { echo "error: slice <도메인> - 예: dm, auth, user" >&2; exit 1; }
    local main_dir="$MAIN/$domain"
    [[ -d "$main_dir" ]] || { echo "error: $main_dir 가 없습니다. 사용 가능: $(ls "$MAIN" | grep -v '\.java$' | tr '\n' ' ')" >&2; exit 1; }

    section "main ($domain)"
    find "$main_dir" -name '*.java' | sort | sed "s|$MAIN/||" | awk -F/ '{
        layer=""; for (i=2; i<NF; i++) layer = layer (layer?"/":"") $i
        printf "  %-24s %s\n", (layer?layer:"."), $NF
    }'

    section "test ($domain)"
    if [[ -d "$TEST/$domain" ]]; then
        find "$TEST/$domain" -name '*.java' | sort | sed "s|$TEST/$domain/|  |"
    else
        echo "  (없음)"
    fi

    section "proto (이름에 $domain 이 들어가는 것)"
    find "$PROTO" -name '*.proto' -path "*$domain*" | sort | sed 's|^|  |' || true
    [[ -n "$(find "$PROTO" -name '*.proto' -path "*$domain*" -print -quit)" ]] || echo "  (없음)"
}

cmd_rpc() {
    local name="${1:-}"
    [[ -n "$name" ]] || { echo "error: rpc <RpcName> - 예: GetUserByToken" >&2; exit 1; }
    local method; method="$(lower_first "$name")"

    section "proto 정의"
    rg -n --no-heading "rpc\s+$name\s*\(" "$PROTO" || echo "  (없음)"

    section "grpc 핸들러"
    rg -n --no-heading -g '*Grpc.java' "\b$method\s*\(" "$MAIN" || echo "  (없음)"

    # 핸들러가 부르는 service 메서드는 RPC 와 이름이 다른 경우가 많다
    # (CreateDirectMessageChannel -> createChannel). 그래서 이름으로 찾지 않고
    # 핸들러 본문을 그대로 보여준 뒤, 거기서 불린 심볼을 뽑는다.
    local handler_file handler_line
    handler_file="$(rg -l -g '*Grpc.java' "\b$method\s*\(" "$MAIN" | head -1 || true)"

    if [[ -n "$handler_file" ]]; then
        handler_line="$(rg -n -g '*Grpc.java' "\b$method\s*\(" "$handler_file" | head -1 | cut -d: -f1)"
        section "핸들러 본문"
        awk -v start="$handler_line" 'NR >= start {
            print "  " NR ": " $0
            n = gsub(/\{/, "{"); m = gsub(/\}/, "}")
            depth += n - m
            if (started && depth <= 0) exit
            if (n > 0) started = 1
        }' "$handler_file"

        section "본문에서 호출한 것"
        awk -v start="$handler_line" 'NR >= start {
            print
            n = gsub(/\{/, "{"); m = gsub(/\}/, "}")
            depth += n - m
            if (started && depth <= 0) exit
            if (n > 0) started = 1
        }' "$handler_file" \
            | rg -o "\b[a-zA-Z_][\w]*\.[a-z][\w]*\s*\(" \
            | sed 's/\s*($//;s/($//' | sort -u | sed 's|^|  |' || echo "  (없음)"
    else
        section "핸들러 본문"
        echo "  (핸들러를 찾지 못했습니다)"
    fi

    section "테스트"
    # 컨벤션상 <Class><Rpc>Test 또는 <Rpc>Test.
    find "$TEST" -name "*${name}Test.java" | sort | sed 's|^|  |' || true
    [[ -n "$(find "$TEST" -name "*${name}Test.java" -print -quit)" ]] || echo "  (없음)"
}

cmd_api() {
    local target="${1:-}"
    [[ -n "$target" ]] || { echo "error: api <클래스명 또는 경로> - 예: UserService" >&2; exit 1; }
    local path
    if [[ -f "$target" ]]; then
        path="$target"
    else
        path="$(find "$MAIN" -name "${target}.java" | head -1)"
    fi
    [[ -n "$path" && -f "$path" ]] || { echo "error: $target 를 $MAIN 아래에서 찾지 못했습니다." >&2; exit 1; }

    section "$path"
    rg -n --no-heading '^\s*(public|protected)\s' "$path" | sed 's|^|  |' || echo "  (public 멤버 없음)"

    section "테스트 동기화"
    # 테스트 컨벤션 판정은 check-test-sync.py 하나에만 두고 여기서는 재구현하지 않는다.
    if [[ -x scripts/check-test-sync.py ]]; then
        scripts/check-test-sync.py "$path" --require "$path" --exit-zero 2>&1 | sed 's|^|  |'
    else
        echo "  (scripts/check-test-sync.py 없음)"
    fi
}

cmd_who() {
    local symbol="${1:-}"
    [[ -n "$symbol" ]] || { echo "error: who <심볼> - 예: JwtTokenProvider, getUser" >&2; exit 1; }

    section "선언"
    rg -n --no-heading \
        -e "\b(class|interface|enum|record)\s+$symbol\b" \
        -e "^\s*(public|protected|private|static|final|\s)*[\w.<>\[\],]+\s+$symbol\s*\(" \
        "$MAIN" || echo "  (없음)"

    # import 는 참조 개수만 부풀리고 분석에 쓸모가 없어 뺀다. 몇 개 걷어냈는지는 알려준다.
    local refs imports
    refs="$(rg -n --no-heading "\b$symbol\b" "$MAIN" "$TEST" \
        | rg -v "\b(class|interface|enum|record)\s+$symbol\b" \
        | rg -v ":\s*import\s" || true)"
    imports="$(rg -c --no-heading "^\s*import\s.*\b$symbol\b" "$MAIN" "$TEST" 2>/dev/null | wc -l)"

    section "참조한 파일 (선언·import 제외, import $imports 개 생략)"
    if [[ -n "$refs" ]]; then
        cut -d: -f1 <<<"$refs" | sort | uniq -c | sort -rn \
            | awk '{printf "  %3d  %s\n", $1, $2}'
        section "참조 상세"
        sed 's|^|  |' <<<"$refs"
    else
        echo "  (없음)"
    fi
}

cmd_layers() {
    local scope="${1:-}"
    local root="$MAIN${scope:+/$scope}"
    [[ -d "$root" ]] || { echo "error: $root 가 없습니다." >&2; exit 1; }

    section "레이어별 파일 수${scope:+ ($scope)}"
    find "$root" -name '*.java' | sed "s|$MAIN/||" | awk -F/ '{
        layer=""; for (i=2; i<NF; i++) layer = layer (layer?"/":"") $i
        count[layer ? layer : "(도메인 루트)"]++
    } END { for (l in count) printf "  %-28s %3d\n", l, count[l] }' | sort -k2 -rn

    section "도메인별 main / test 파일 수"
    for domain in $(ls "$MAIN" | grep -v '\.java$'); do
        local m t
        m=$(find "$MAIN/$domain" -name '*.java' 2>/dev/null | wc -l)
        t=$(find "$TEST/$domain" -name '*.java' 2>/dev/null | wc -l)
        printf "  %-12s main %3d   test %3d\n" "$domain" "$m" "$t"
    done
}

case "${1:-}" in
    slice)  shift; cmd_slice "$@" ;;
    rpc)    shift; cmd_rpc "$@" ;;
    api)    shift; cmd_api "$@" ;;
    who)    shift; cmd_who "$@" ;;
    layers) shift; cmd_layers "$@" ;;
    -h|--help|"") usage ;;
    *) echo "error: 알 수 없는 명령 '$1'" >&2; echo >&2; usage >&2; exit 1 ;;
esac
