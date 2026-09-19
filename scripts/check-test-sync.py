#!/usr/bin/env python3
"""소스의 public 메서드와 테스트 클래스가 어긋났는지 검사한다.

이 리포의 컨벤션 — 메서드 하나당 테스트 클래스 하나를, 소스와 같은 패키지에 둔다.

    src/main/java/com/danmalgi/backend/user/service/UserService.java
        public User getUser(...)          -> UserServiceGetUserTest
        public void verifyNameAndTag(...) -> UserServiceVerifyNameAndTagTest
    src/test/java/com/danmalgi/backend/user/service/UserServiceGetUserTest.java

검출하는 것은 두 가지다.

    MISS   : public 메서드에 대응하는 테스트 클래스가 없다.
    ORPHAN : 테스트 클래스는 있는데 대응하는 public 메서드가 없다. (이름 변경/삭제)

오탐을 줄이려고 기본값은 '이미 테스트가 하나라도 있는 클래스'만 본다. DTO, 설정,
엔티티처럼 애초에 테스트를 두지 않는 클래스까지 MISS 로 잡지 않기 위해서다.
새로 만든 클래스까지 강제하려면 --require 로 경로 패턴을 준다.

사용법:
  scripts/check-test-sync.py                          # 리포 전체
  scripts/check-test-sync.py path/to/Foo.java ...     # 지정한 파일만
  scripts/check-test-sync.py --all                    # 레이어 제한 없이 전수 검사
  scripts/check-test-sync.py --stdin                  # PostToolUse 훅 JSON 을 stdin 으로
  scripts/check-test-sync.py --exit-zero              # 보고만 하고 성공 종료
  scripts/check-test-sync.py --json

PostToolUse 훅으로 쓸 때는 --stdin 을 주면 훅 JSON 의 tool_input.file_path 를
읽어 그 파일만 검사한다. (--stdin 없이는 stdin 을 건드리지 않는다)

종료 코드: 0 동기화됨 / 2 어긋남 (훅에서 Claude 에게 피드백으로 되돌아간다)
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MAIN_ROOT = REPO_ROOT / "src/main/java"
TEST_ROOT = REPO_ROOT / "src/test/java"

# 클래스 본문(depth 1)에 있는 public 메서드만 본다. 중첩 클래스 안쪽은 제외.
# 어노테이션이 public 과 반환 타입 사이에 낄 수 있다: public @Nullable StatusException foo()
MODIFIERS = r"(?:(?:static|final|synchronized|abstract|native|default)\s+|@\w+(?:\([^)]*\))?\s+)*"
TYPE = r"(?:<[^>]+>\s+)?[\w.<>\[\],\s?&]+?\s+"

CLASS_METHOD_RE = re.compile(
    r"^\s*public\s+" + MODIFIERS +
    r"(?!class\b|interface\b|enum\b|record\b)" + TYPE + r"(\w+)\s*\(",
)
# 인터페이스 본문은 public 키워드가 없어도 전부 public 이다. private 만 제외.
INTERFACE_METHOD_RE = re.compile(
    r"^\s*(?!private\b|@)(?:public\s+)?" + MODIFIERS +
    r"(?!class\b|interface\b|enum\b|record\b)" + TYPE + r"(\w+)\s*\(",
)
TYPE_DECL_RE = re.compile(r"\b(class|interface|enum|record)\s+(\w+)")

# 메서드당 테스트 클래스 하나 컨벤션이 실제로 지켜지는 레이어만 기본 검사 대상으로 둔다.
# (측정 결과 이 네 곳은 테스트 52개 중 51개가 메서드명과 일치한다. interceptor/entity 등
#  나머지 레이어는 시나리오 이름을 쓰므로 넣으면 오탐만 늘어난다.)
DEFAULT_LAYERS = ("service", "grpc", "grpc/mapper", "grpc/validator")

USE_COLOR = sys.stdout.isatty() and os.environ.get("NO_COLOR") is None


def paint(text: str, code: str) -> str:
    return f"\033[{code}m{text}\033[0m" if USE_COLOR else text


MISS = paint(" MISS ", "31")
ORPHAN = paint("ORPHAN", "33")


@dataclass
class Finding:
    kind: str           # "MISS" | "ORPHAN"
    source: Path        # 소스 파일 (리포 기준 상대경로)
    member: str         # MISS 면 메서드명, ORPHAN 이면 테스트 클래스명
    expected: str       # MISS 면 기대 테스트 클래스명, ORPHAN 이면 사라진 메서드명


@dataclass
class Result:
    checked: list[Path] = field(default_factory=list)
    findings: list[Finding] = field(default_factory=list)


def strip_noise(text: str) -> str:
    """문자열/주석 안의 중괄호가 depth 계산을 망치지 않도록 지운다."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def public_methods(path: Path) -> list[str]:
    """최상위 타입 본문의 public 메서드 이름을 돌려준다."""
    class_name = path.stem
    text = strip_noise(path.read_text(encoding="utf-8"))

    # 최상위 타입이 인터페이스면 규칙이 달라진다.
    kind = "class"
    for match in TYPE_DECL_RE.finditer(text):
        if match.group(2) == class_name:
            kind = match.group(1)
            break
    if kind in ("enum", "record"):
        return []  # 값 타입에 메서드별 테스트를 요구하지 않는다.
    pattern = INTERFACE_METHOD_RE if kind == "interface" else CLASS_METHOD_RE

    methods: list[str] = []
    depth = 0
    for line in text.splitlines():
        # 메서드 판정은 이 줄의 중괄호를 세기 전 depth 로 한다.
        if depth == 1:
            match = pattern.match(line)
            if match and match.group(1) != class_name:  # 생성자 제외
                methods.append(match.group(1))
        depth += line.count("{") - line.count("}")
    return methods


def test_dir_for(source: Path) -> Path:
    return TEST_ROOT / source.relative_to(MAIN_ROOT).parent


def expected_test_name(class_name: str, method: str) -> str:
    return f"{class_name}{method[0].upper()}{method[1:]}Test"


def sibling_classes(source: Path) -> list[str]:
    """같은 패키지의 다른 클래스들. 테스트 이름 접두어가 겹칠 때 구분하는 데 쓴다."""
    return sorted(
        (p.stem for p in source.parent.glob("*.java") if p != source),
        key=len,
        reverse=True,
    )


def in_layer(source: Path, layers: tuple[str, ...]) -> bool:
    package_dir = source.parent.relative_to(MAIN_ROOT).as_posix()
    return any(package_dir == layer or package_dir.endswith("/" + layer) for layer in layers)


def check(source: Path, require_patterns: list[str], layers: tuple[str, ...], check_all: bool) -> list[Finding]:
    class_name = source.stem
    test_dir = test_dir_for(source)
    rel = source.relative_to(REPO_ROOT)

    all_tests = {p.stem for p in test_dir.glob("*Test.java")} if test_dir.is_dir() else set()
    siblings = sibling_classes(source)

    # 접두어 있는 형태: <Class><Method>Test.
    # 같은 패키지에 더 긴 이름의 클래스가 있으면 그쪽 테스트다. (UserService vs UserServiceHelper)
    longer = [x for x in siblings if x.startswith(class_name)]
    prefixed = {
        t for t in all_tests
        if t.startswith(class_name) and not any(t.startswith(x) for x in longer)
    }
    # 접두어 없는 형태: <Method>Test. 이 리포는 mapper/validator 에서 두 방식을 섞어 쓴다.
    unprefixed = {t for t in all_tests if not any(t.startswith(x) for x in [class_name, *siblings])}

    # 접미사 없는 <Class>Test 는 클래스 단위 테스트다. 특정 메서드에 묶이지 않으므로
    # MISS 를 채워주지도, ORPHAN 으로 잡지도 않는다.
    prefixed.discard(f"{class_name}Test")

    forced = any(fnmatch.fnmatch(str(rel), pat) for pat in require_patterns)
    if not forced:
        if check_all:
            if not (prefixed or unprefixed):
                return []  # 애초에 테스트를 두지 않는 클래스.
        elif not in_layer(source, layers):
            return []

    methods = public_methods(source)
    findings: list[Finding] = []

    wanted_prefixed, wanted_plain = {}, {}
    for method in methods:
        pascal = f"{method[0].upper()}{method[1:]}"
        wanted_prefixed[f"{class_name}{pascal}Test"] = method
        wanted_plain[f"{pascal}Test"] = method

    for test_name, method in wanted_prefixed.items():
        if test_name not in prefixed and f"{test_name[len(class_name):]}" not in unprefixed:
            findings.append(Finding("MISS", rel, f"{method}()", test_name))

    for test_name in sorted(prefixed - set(wanted_prefixed)):
        findings.append(Finding("ORPHAN", rel, test_name, _method_guess(test_name[len(class_name):])))

    # 접두어 없는 테스트는 어느 클래스 것인지 단정할 수 없다. 패키지에 클래스가 하나뿐일 때만 본다.
    if not siblings:
        for test_name in sorted(unprefixed - set(wanted_plain)):
            findings.append(Finding("ORPHAN", rel, test_name, _method_guess(test_name)))

    return findings


def _method_guess(test_name: str) -> str:
    suffix = test_name[:-len("Test")] if test_name.endswith("Test") else test_name
    return f"{suffix[0].lower()}{suffix[1:]}()" if suffix else "?"


def resolve_target(raw: str) -> Path | None:
    """인자로 받은 경로를 검사 대상 소스 파일로 바꾼다. 테스트 파일이면 소스로 되돌린다."""
    path = Path(raw)
    if not path.is_absolute():
        path = (REPO_ROOT / path).resolve()
    if path.suffix != ".java" or not path.exists():
        return None

    if path.is_relative_to(MAIN_ROOT):
        return path

    if path.is_relative_to(TEST_ROOT):
        # 테스트를 고쳤으면 그 테스트가 딸린 소스 클래스를 본다.
        main_dir = MAIN_ROOT / path.relative_to(TEST_ROOT).parent
        if not main_dir.is_dir():
            return None
        stem = path.stem
        candidates = [p for p in main_dir.glob("*.java") if stem.startswith(p.stem)]
        return max(candidates, key=lambda p: len(p.stem)) if candidates else None

    return None


def targets_from_stdin() -> list[str]:
    """PostToolUse 훅 JSON 에서 편집된 파일 경로를 뽑는다."""
    try:
        payload = json.loads(sys.stdin.read() or "{}")
    except json.JSONDecodeError:
        return []
    tool_input = payload.get("tool_input") or {}
    paths = [tool_input.get("file_path")]
    # MultiEdit 등 여러 파일을 담는 형태도 받아준다.
    for edit in tool_input.get("edits") or []:
        if isinstance(edit, dict):
            paths.append(edit.get("file_path"))
    return [p for p in paths if p]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="소스 public 메서드와 테스트 클래스의 동기화 검사",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__.split("사용법:")[1] if "사용법:" in __doc__ else None,
    )
    parser.add_argument("files", nargs="*", help="검사할 .java 경로 (없으면 리포 전체)")
    parser.add_argument("--require", action="append", default=[],
                        help="검사 범위 밖이어도 강제로 검사할 경로 glob (반복 가능)")
    parser.add_argument("--layer", action="append", default=[],
                        help=f"검사할 패키지 레이어 (기본: {', '.join(DEFAULT_LAYERS)})")
    parser.add_argument("--all", dest="check_all", action="store_true",
                        help="레이어 제한 없이, 테스트가 하나라도 있는 모든 클래스를 검사")
    parser.add_argument("--stdin", action="store_true",
                        help="stdin 으로 들어온 훅 JSON 에서 대상 파일을 읽는다 (PostToolUse 훅용)")
    parser.add_argument("--exit-zero", action="store_true", help="어긋나도 0 으로 종료")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    if not MAIN_ROOT.is_dir():
        print(f"error: {MAIN_ROOT} 가 없습니다.", file=sys.stderr)
        return 2

    # stdin 은 --stdin 일 때만 읽는다. 파이프로 실행될 때 무한 대기하지 않게 하려는 것.
    if args.stdin:
        # 훅 모드에서는 대상을 못 뽑아도 전체 검사로 넘어가지 않는다.
        # 페이로드가 깨졌다고 리포 전체를 훅으로 막아버리면 안 된다.
        raw_targets = args.files or targets_from_stdin()
        if not raw_targets:
            return 0
    else:
        raw_targets = args.files

    if raw_targets:
        sources = [s for s in (resolve_target(r) for r in raw_targets) if s]
        if not sources:
            return 0  # 자바 소스와 무관한 편집이면 조용히 통과.
    else:
        sources = sorted(MAIN_ROOT.rglob("*.java"))

    layers = tuple(args.layer) if args.layer else DEFAULT_LAYERS

    result = Result()
    for source in sources:
        result.checked.append(source.relative_to(REPO_ROOT))
        result.findings.extend(check(source, args.require, layers, args.check_all))

    if args.json:
        print(json.dumps({
            "checked": [str(p) for p in result.checked],
            "findings": [
                {"kind": f.kind, "source": str(f.source), "member": f.member, "expected": f.expected}
                for f in result.findings
            ],
        }, ensure_ascii=False, indent=2))
    else:
        render(result)

    if result.findings and not args.exit_zero:
        return 2
    return 0


def render(result: Result) -> None:
    if not result.findings:
        print(f"테스트 동기화 이상 없음 ({len(result.checked)}개 소스 검사)")
        return

    # 훅 피드백으로 되돌아가므로 stderr 로 낸다.
    out = sys.stderr
    current = None
    for f in sorted(result.findings, key=lambda f: (str(f.source), f.kind, f.member)):
        if f.source != current:
            current = f.source
            print(f"\n  {current}", file=out)
        if f.kind == "MISS":
            print(f"    [{MISS}] {f.member}  ->  {f.expected} 없음", file=out)
        else:
            print(f"    [{ORPHAN}] {f.member}", file=out)
            print(f"             ->  {f.expected} 없음 (이름 변경/삭제?)", file=out)

    miss = sum(1 for f in result.findings if f.kind == "MISS")
    orphan = len(result.findings) - miss
    print(f"\n동기화 {len(result.findings)}건 어긋남 (MISS {miss}, ORPHAN {orphan})", file=out)
    print("테스트를 추가하거나, 이름이 바뀐 테스트를 현재 메서드명에 맞추세요.", file=out)


if __name__ == "__main__":
    sys.exit(main())
