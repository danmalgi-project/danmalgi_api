#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["grpcio>=1.60", "grpcio-reflection>=1.60", "protobuf>=4.25"]
# ///
"""가짜 Apple 토큰으로 Apple 로그인 RPC 를 끝까지 호출한다. (로컬 전용)

흐름 — users / user_oauth_identities 행은 Register 에서 생긴다.
Authorization 만 호출하면 Redis 의 pending 세션과 임시 토큰까지다.

  1. scripts/fake-apple.py 의 /_test/mint 에서 id_token + authorization_code 를 받는다
  2. auth.v1.AuthService/Authorization 호출 -> access_token
  3. 그 토큰을 Bearer 로 실어 auth.v1.AuthService/Register 호출 -> users INSERT

요청 메시지는 서버 리플렉션으로 받은 디스크립터로 만든다. proto 가 서브모듈이라
필드 번호를 이 스크립트에 베껴 두면 스키마가 바뀔 때 조용히 어긋난다.

사전 준비:
  scripts/fake-apple.py                                   # 다른 터미널
  export APPLE_PRIVATE_KEY="$(scripts/fake-apple.py --print-client-key)"
  scripts/run.sh --profile local                          # 또 다른 터미널

사용법:
  scripts/apple-login.py                                  # 랜덤 sub 로 신규 가입 1회
  scripts/apple-login.py --sub test-apple-1               # sub 고정 (두 번째부터 기존 유저 경로)
  scripts/apple-login.py --no-register                    # Authorization 까지만
  scripts/apple-login.py --raw-nonce abc123               # nonce 검사까지 태운다
  scripts/apple-login.py --addr 10.0.0.5:8080
"""

from __future__ import annotations

import argparse
import json
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

import grpc
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory
from grpc_reflection.v1alpha import reflection_pb2

AUTH_SERVICE = "auth.v1.AuthService"
REFLECTION_PATHS = [
    "/grpc.reflection.v1.ServerReflection/ServerReflectionInfo",
    "/grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo",
]


# --------------------------------------------------------------------------- 가짜 Apple


def mint(mint_url: str, *, sub: str, aud: str, email: str, raw_nonce: str | None) -> tuple[str, str]:
    query = {"sub": sub, "aud": aud, "email": email}
    if raw_nonce:
        query["raw_nonce"] = raw_nonce
    url = f"{mint_url}?{urllib.parse.urlencode(query)}"
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            body = json.loads(response.read())
    except urllib.error.URLError as exc:
        raise SystemExit(f"error: 가짜 Apple 서버에 붙지 못했습니다 ({url}): {exc}\n"
                         f"       scripts/fake-apple.py 를 먼저 띄우세요.")
    return body["id_token"], body["authorization_code"]


# --------------------------------------------------------------------------- 디스크립터


def build_pool(channel: grpc.Channel, timeout: float) -> descriptor_pool.DescriptorPool:
    """리플렉션으로 받은 FileDescriptorProto 를 의존 순서에 맞춰 쌓는다."""
    raw_files = _fetch_descriptors(channel, timeout)
    if not raw_files:
        raise SystemExit("error: 서버 리플렉션에서 디스크립터를 받지 못했습니다.\n"
                         "       spring.grpc.server.reflection.enabled 를 확인하세요.")

    pool = descriptor_pool.DescriptorPool()
    pending = []
    for raw in raw_files:
        fd = descriptor_pb2.FileDescriptorProto()
        fd.ParseFromString(raw)
        pending.append(fd)

    # 리플렉션 응답의 파일 순서는 보장되지 않는다. 더 못 넣을 때까지 반복한다.
    while pending:
        progressed = []
        for fd in pending:
            try:
                pool.Add(fd)
            except (TypeError, KeyError):
                progressed.append(fd)  # 의존 파일이 아직 없다. 다음 바퀴에.
        if len(progressed) == len(pending):
            missing = ", ".join(fd.name for fd in progressed)
            raise SystemExit(f"error: 디스크립터 의존을 풀지 못했습니다: {missing}")
        pending = progressed
    return pool


def _fetch_descriptors(channel: grpc.Channel, timeout: float) -> list[bytes]:
    request = reflection_pb2.ServerReflectionRequest(file_containing_symbol=AUTH_SERVICE)
    for path in REFLECTION_PATHS:
        stub = channel.stream_stream(
            path,
            request_serializer=lambda m: m.SerializeToString(),
            response_deserializer=reflection_pb2.ServerReflectionResponse.FromString,
        )
        try:
            for response in stub(iter([request]), timeout=timeout):
                if response.HasField("error_response"):
                    break
                return list(response.file_descriptor_response.file_descriptor_proto)
        except grpc.RpcError:
            continue  # 리플렉션 service 버전이 다르면 다음 경로로.
    return []


# --------------------------------------------------------------------------- 호출


def call(channel: grpc.Channel, method: str, request, response_class, timeout: float, token: str | None):
    metadata = [("authorization", f"Bearer {token}")] if token else None
    rpc = channel.unary_unary(
        f"/{AUTH_SERVICE}/{method}",
        request_serializer=lambda m: m.SerializeToString(),
        response_deserializer=response_class.FromString,
    )
    try:
        return rpc(request, timeout=timeout, metadata=metadata)
    except grpc.RpcError as exc:
        raise SystemExit(f"error: {method} 실패 — {exc.code().name}: {(exc.details() or '').strip()}")


def describe_user(pool: descriptor_pool.DescriptorPool, user) -> str:
    status = pool.FindEnumTypeByName("user.v1.UserStatus").values_by_number.get(user.status)
    return (f"id={user.id} name={user.name!r} tag={user.tag!r} "
            f"email={user.email!r} status={status.name if status else user.status}")


# --------------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(
        description="가짜 Apple 토큰으로 Apple 로그인 RPC 호출",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__.split("사용법:")[1] if "사용법:" in __doc__ else None,
    )
    parser.add_argument("--addr", default="localhost:8080")
    parser.add_argument("--mint-url", default="http://127.0.0.1:9099/_test/mint")
    parser.add_argument("--sub", default=None, help="Apple sub. 생략하면 매번 새로 만든다")
    parser.add_argument("--aud", default="com.danmalgi.local", help="application-local.yaml 의 audiences 와 맞춘다")
    parser.add_argument("--email", default="local@privaterelay.appleid.com")
    parser.add_argument("--raw-nonce", default=None)
    parser.add_argument("--device-id", default="local-device-1")
    parser.add_argument("--nickname", default="오해수", help="생략하면 무작위")
    parser.add_argument("--tag", default="1234", help="생략하면 무작위")
    parser.add_argument("--no-register", action="store_true", help="Authorization 까지만 하고 멈춘다")
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    suffix = secrets.token_hex(4)
    sub = args.sub or f"test-apple-{suffix}"
    nickname = args.nickname or f"로컬{suffix}"
    tag = args.tag or suffix

    id_token, authorization_code = mint(
        args.mint_url, sub=sub, aud=args.aud, email=args.email, raw_nonce=args.raw_nonce)
    print(f"==> 가짜 Apple 토큰 발급: sub={sub} aud={args.aud}")

    channel = grpc.insecure_channel(args.addr)
    try:
        grpc.channel_ready_future(channel).result(timeout=args.timeout)
    except grpc.FutureTimeoutError:
        print(f"error: {args.addr} 에 붙지 못했습니다. scripts/run.sh --profile local 로 띄웠는지 확인하세요.",
              file=sys.stderr)
        return 2

    pool = build_pool(channel, args.timeout)
    message = lambda name: message_factory.GetMessageClass(pool.FindMessageTypeByName(name))  # noqa: E731
    oauth_type = pool.FindEnumTypeByName("user.v1.OauthType").values_by_name["APPLE"].number

    authorization = call(channel, "Authorization", message("auth.v1.AuthorizationRequest")(
        id_token=id_token,
        device_id=args.device_id,
        oauth_type=oauth_type,
        authorization_code=authorization_code,
        raw_nonce=args.raw_nonce or "",
    ), message("auth.v1.AuthorizationResponse"), args.timeout, None)
    print(f"==> Authorization OK  {describe_user(pool, authorization.user)}")

    if args.no_register:
        print("    (--no-register 이므로 여기까지. DB 행은 아직 없다)")
        return 0

    register = call(channel, "Register", message("auth.v1.RegisterRequest")(
        nickname=nickname,
        tag=tag,
    ), message("auth.v1.RegisterResponse"), args.timeout, authorization.access_token)
    print(f"==> Register OK  {describe_user(pool, register.user)}")
    print()
    print("DB 확인:")
    print(f"  select * from users where id = {register.user.id};")
    print(f"  select * from user_oauth_identities where provider_subject = '{sub}';")
    return 0


if __name__ == "__main__":
    sys.exit(main())
