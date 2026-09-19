#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["grpcio>=1.60", "grpcio-reflection>=1.60", "protobuf>=4.25"]
# ///
"""gRPC method endpoint 도달 확인 스크립트.

세 가지를 한 번에 본다.

1. 리플렉션으로 서버가 실제 등록한 service/method 목록을 읽는다.
2. src/main/proto 의 .proto 정의와 대조한다. (proto 에만 있는 것 / 서버에만 있는 것)
3. 각 method 에 빈 요청을 던져 응답 status 로 도달 여부를 판정한다.

판정 기준 — "도달"은 핸들러나 인터셉터가 응답했다는 뜻이지 성공이 아니다.
  UNIMPLEMENTED            -> 미도달. 라우팅에 없다.
  UNAVAILABLE / 연결 실패  -> 서버에 못 붙었다.
  그 외 (UNAUTHENTICATED,
  INVALID_ARGUMENT, OK ...) -> 도달.

기본값은 토큰 없이 던지므로 external service 는 GrpcAuthInterceptor 가
핸들러 앞에서 UNAUTHENTICATED 로 끊는다. 즉 부작용 없이 라우팅만 확인한다.
  주의: AuthService/Authorization 은 인터셉터를 통과하므로 핸들러가 실제로 실행된다.
        --token 을 주면 모든 핸들러가 실제로 실행된다. (데이터가 바뀔 수 있다)

사용법:
  scripts/check-grpc.py                         # localhost:8080 에 전체 확인
  scripts/check-grpc.py --addr 10.0.0.5:8080    # 대상 지정
  scripts/check-grpc.py --service dm.v1.DirectMessageService
  scripts/check-grpc.py --list                  # 목록만 보고 probe 는 생략
  scripts/check-grpc.py --token "$JWT"          # 인증 통과시켜 핸들러까지 (부작용 주의)
  scripts/check-grpc.py --json                  # 결과를 JSON 으로
  scripts/check-grpc.py --strict                # proto 에만 있는 method 도 실패로 취급

종료 코드: 0 정상 / 1 미도달 있음 / 2 서버 연결 실패
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

import grpc
from google.protobuf import descriptor_pb2
from grpc_reflection.v1alpha import reflection_pb2

REPO_ROOT = Path(__file__).resolve().parent.parent
PROTO_DIRS = [REPO_ROOT / "src/main/proto/external", REPO_ROOT / "src/main/proto/internal"]

# grpc-java 는 v1 과 v1alpha 를 따로 등록한다. 메시지 포맷은 같으므로 경로만 바꿔 재시도한다.
REFLECTION_PATHS = [
    "/grpc.reflection.v1.ServerReflection/ServerReflectionInfo",
    "/grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo",
]

# 서버가 스스로 등록하는 인프라 service. proto 대조 대상에서 뺀다.
INFRA_SERVICES = {
    "grpc.reflection.v1.ServerReflection",
    "grpc.reflection.v1alpha.ServerReflection",
    "grpc.health.v1.Health",
    "grpc.channelz.v1.Channelz",
}

# 도달로 인정하지 않는 status.
NOT_REACHED = {grpc.StatusCode.UNIMPLEMENTED}
NO_CONNECTION = {grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.DEADLINE_EXCEEDED}

IDENTITY = lambda b: b  # noqa: E731  - 페이로드를 해석하지 않고 그대로 주고받는다.

USE_COLOR = sys.stdout.isatty() and os.environ.get("NO_COLOR") is None


def paint(text: str, code: str) -> str:
    return f"\033[{code}m{text}\033[0m" if USE_COLOR else text


# 열 정렬이 깨지지 않도록 색을 입히기 전에 폭을 맞춘다.
OK = paint(" OK ", "32")
FAIL = paint("FAIL", "31")
WARN = paint("WARN", "33")


@dataclass
class Method:
    service: str
    name: str
    client_streaming: bool = False
    server_streaming: bool = False

    @property
    def full(self) -> str:
        return f"{self.service}/{self.name}"

    @property
    def path(self) -> str:
        return f"/{self.service}/{self.name}"

    @property
    def kind(self) -> str:
        if self.client_streaming and self.server_streaming:
            return "bidi"
        if self.server_streaming:
            return "server-stream"
        if self.client_streaming:
            return "client-stream"
        return "unary"


@dataclass
class Probe:
    method: Method
    code: str
    detail: str
    reached: bool


@dataclass
class Report:
    addr: str
    reflection: bool = False
    proto_only: list[str] = field(default_factory=list)
    server_only: list[str] = field(default_factory=list)
    probes: list[Probe] = field(default_factory=list)


# --------------------------------------------------------------------------- proto

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
SERVICE_RE = re.compile(r"^\s*service\s+(\w+)\s*\{", re.MULTILINE)
RPC_RE = re.compile(
    r"^\s*rpc\s+(\w+)\s*\(\s*(stream\s+)?[\w.]+\s*\)\s*returns\s*\(\s*(stream\s+)?[\w.]+\s*\)",
    re.MULTILINE,
)


def parse_protos() -> list[Method]:
    """.proto 에 선언된 method 를 모은다. 서버 유무와 무관한 '기대 목록'이다."""
    methods: list[Method] = []
    for proto_dir in PROTO_DIRS:
        for path in sorted(proto_dir.rglob("*.proto")):
            text = path.read_text(encoding="utf-8")
            pkg_match = PACKAGE_RE.search(text)
            package = pkg_match.group(1) if pkg_match else ""

            # service 블록 단위로 잘라서 그 안의 rpc 만 귀속시킨다.
            starts = [(m.start(), m.group(1)) for m in SERVICE_RE.finditer(text)]
            for idx, (start, service_name) in enumerate(starts):
                end = starts[idx + 1][0] if idx + 1 < len(starts) else len(text)
                full_service = f"{package}.{service_name}" if package else service_name
                for rpc in RPC_RE.finditer(text[start:end]):
                    methods.append(
                        Method(
                            service=full_service,
                            name=rpc.group(1),
                            client_streaming=bool(rpc.group(2)),
                            server_streaming=bool(rpc.group(3)),
                        )
                    )
    return methods


# --------------------------------------------------------------------------- reflection


class ReflectionUnsupported(Exception):
    """이 경로의 리플렉션 service 가 없다. 다른 버전 경로로 재시도한다."""


def reflect(channel: grpc.Channel, timeout: float) -> list[Method] | None:
    """리플렉션으로 서버가 실제 등록한 method 를 읽는다. 꺼져 있으면 None."""
    for path in REFLECTION_PATHS:
        stub = channel.stream_stream(
            path,
            request_serializer=lambda m: m.SerializeToString(),
            response_deserializer=reflection_pb2.ServerReflectionResponse.FromString,
        )
        try:
            services = _list_services(stub, timeout)
            methods: list[Method] = []
            for service in services:
                methods.extend(_methods_of(stub, service, timeout))
            return methods
        except ReflectionUnsupported:
            continue  # 이 버전이 아니면 다음 경로로.
        except grpc.RpcError as exc:
            if exc.code() in NO_CONNECTION:
                raise
            continue  # 이 버전이 아니면 다음 경로로.
    return None


def _list_services(stub, timeout: float) -> list[str]:
    request = reflection_pb2.ServerReflectionRequest(list_services="")
    for response in stub(iter([request]), timeout=timeout):
        if response.HasField("error_response"):
            raise ReflectionUnsupported(response.error_response.error_message)
        return [s.name for s in response.list_services_response.service]
    return []


def _methods_of(stub, service: str, timeout: float) -> list[Method]:
    request = reflection_pb2.ServerReflectionRequest(file_containing_symbol=service)
    for response in stub(iter([request]), timeout=timeout):
        if response.HasField("error_response"):
            return []
        for raw in response.file_descriptor_response.file_descriptor_proto:
            fd = descriptor_pb2.FileDescriptorProto()
            fd.ParseFromString(raw)
            for svc in fd.service:
                full = f"{fd.package}.{svc.name}" if fd.package else svc.name
                if full != service:
                    continue
                return [
                    Method(full, m.name, m.client_streaming, m.server_streaming)
                    for m in svc.method
                ]
    return []


# --------------------------------------------------------------------------- probe


def probe(channel: grpc.Channel, method: Method, timeout: float, token: str | None) -> Probe:
    """빈 요청을 던지고 돌아온 status 로 도달 여부를 본다.

    unary_stream 으로 통일한다 — unary 응답은 메시지 1개짜리 스트림과 같고,
    client-stream/bidi 도 '1개 보내고 half-close' 로 성립한다.
    proto3 는 모든 필드가 optional 이라 빈 바이트열이 어떤 메시지로도 파싱된다.
    """
    metadata = [("authorization", f"Bearer {token}")] if token else None
    call = channel.unary_stream(method.path, request_serializer=IDENTITY, response_deserializer=IDENTITY)
    try:
        for _ in call(b"", timeout=timeout, metadata=metadata):
            break  # 첫 응답만 확인하고 끊는다.
        return Probe(method, "OK", "", True)
    except grpc.RpcError as exc:
        code = exc.code()
        detail = (exc.details() or "").strip().splitlines()
        return Probe(method, code.name, detail[0] if detail else "", code not in NOT_REACHED)


# --------------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(
        description="gRPC method endpoint 도달 확인",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__.split("사용법:")[1] if "사용법:" in __doc__ else None,
    )
    parser.add_argument("--addr", default=os.environ.get("GRPC_ADDR", "localhost:8080"))
    parser.add_argument("--token", default=os.environ.get("GRPC_TOKEN"), help="JWT (핸들러가 실제 실행된다)")
    parser.add_argument("--service", action="append", default=[], help="이 service 만 확인 (반복 가능)")
    parser.add_argument("--ignore-service", action="append", default=[], help="대조에서 제외할 service")
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--list", action="store_true", help="목록만 출력하고 probe 생략")
    parser.add_argument("--no-reflect", action="store_true", help="리플렉션 없이 proto 목록만 사용")
    parser.add_argument("--strict", action="store_true", help="proto 에만 있는 method 도 실패로 취급")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    report = Report(addr=args.addr)
    proto_methods = parse_protos()
    if not proto_methods:
        print(f"error: {PROTO_DIRS[0]} 에서 proto 를 읽지 못했습니다. scripts/build-proto.sh 를 먼저 실행하세요.", file=sys.stderr)
        return 2

    channel = grpc.insecure_channel(args.addr)
    try:
        grpc.channel_ready_future(channel).result(timeout=args.timeout)
    except grpc.FutureTimeoutError:
        print(f"error: {args.addr} 에 연결하지 못했습니다. 서버가 떠 있는지 확인하세요. (scripts/run.sh)", file=sys.stderr)
        return 2

    server_methods: list[Method] | None = None
    if not args.no_reflect:
        try:
            server_methods = reflect(channel, args.timeout)
        except grpc.RpcError as exc:
            print(f"error: 리플렉션 호출 실패 ({exc.code().name}). --no-reflect 로 proto 목록만 쓸 수 있습니다.", file=sys.stderr)
            return 2
        if server_methods is None:
            print(f"{WARN} 리플렉션이 꺼져 있습니다. proto 목록만 사용합니다.", file=sys.stderr)

    report.reflection = server_methods is not None

    ignored = set(args.ignore_service)
    proto_index = {m.full: m for m in proto_methods if m.service not in ignored}
    if server_methods is None:
        targets = list(proto_index.values())
    else:
        server_index = {
            m.full: m
            for m in server_methods
            if m.service not in INFRA_SERVICES and m.service not in ignored
        }
        report.proto_only = sorted(set(proto_index) - set(server_index))
        report.server_only = sorted(set(server_index) - set(proto_index))
        # 서버에 있는 것은 서버 기준(streaming 여부가 정확)으로, proto 에만 있는 것도 probe 한다.
        targets = list(server_index.values()) + [proto_index[f] for f in report.proto_only]

    if args.service:
        wanted = set(args.service)
        targets = [m for m in targets if m.service in wanted]
        # 대조 결과도 같은 범위로 좁힌다. 안 그러면 필터와 무관한 service 가 섞여 보인다.
        report.proto_only = [f for f in report.proto_only if f.split("/", 1)[0] in wanted]
        report.server_only = [f for f in report.server_only if f.split("/", 1)[0] in wanted]
        if not targets:
            print(f"error: --service 로 지정한 {', '.join(wanted)} 에 해당하는 method 가 없습니다.", file=sys.stderr)
            return 2

    targets.sort(key=lambda m: m.full)

    if not args.list:
        if args.token:
            print(f"{WARN} --token 이 주어져 핸들러가 실제로 실행됩니다. 데이터가 변경될 수 있습니다.\n", file=sys.stderr)
        for method in targets:
            report.probes.append(probe(channel, method, args.timeout, args.token))

    channel.close()

    if args.json:
        print(json.dumps(_as_dict(report), ensure_ascii=False, indent=2))
    else:
        _render(report, targets, args)

    unreached = [p for p in report.probes if not p.reached]
    if unreached:
        return 1
    if args.strict and report.proto_only:
        return 1
    return 0


def _as_dict(report: Report) -> dict:
    return {
        "addr": report.addr,
        "reflection": report.reflection,
        "proto_only": report.proto_only,
        "server_only": report.server_only,
        "probes": [
            {"method": p.method.full, "kind": p.method.kind, "code": p.code,
             "detail": p.detail, "reached": p.reached}
            for p in report.probes
        ],
    }


def _render(report: Report, targets: list[Method], args) -> None:
    print(f"대상: {report.addr}   리플렉션: {'사용' if report.reflection else '미사용'}")
    print()

    if args.list:
        current = None
        for method in targets:
            if method.service != current:
                current = method.service
                print(f"  {current}")
            print(f"    - {method.name} ({method.kind})")
        print(f"\n총 {len(targets)}개 method")
    else:
        width = max((len(p.method.full) for p in report.probes), default=0)
        current = None
        for p in report.probes:
            if p.method.service != current:
                current = p.method.service
                print(f"  {current}")
            mark = OK if p.reached else FAIL
            detail = f"  {p.detail}" if p.detail and not p.reached else ""
            print(f"    [{mark}] {p.method.full:<{width}}  {p.code}{detail}")
        reached = sum(1 for p in report.probes if p.reached)
        print(f"\n도달 {reached}/{len(report.probes)}")

    if report.proto_only:
        print(f"\n{WARN} proto 에 있으나 서버에 없음 ({len(report.proto_only)}개):")
        for full in report.proto_only:
            print(f"    - {full}")
        print("    서버가 구현하지 않는 클라이언트 전용 proto 라면 --ignore-service 로 제외하세요.")

    if report.server_only:
        print(f"\n{WARN} 서버에만 있고 proto 에 없음 ({len(report.server_only)}개):")
        for full in report.server_only:
            print(f"    - {full}")


if __name__ == "__main__":
    sys.exit(main())
