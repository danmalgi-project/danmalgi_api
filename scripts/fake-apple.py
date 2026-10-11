#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.10,<3.14"
# dependencies = ["cryptography>=42", "pyjwt>=2.8"]
# ///
"""로컬 전용 가짜 Apple 서버.

Apple Developer 계정 없이 Apple 로그인 RPC 를 끝까지 돌려 보기 위한 스텁이다.
서버는 `--profile local` 로 띄워야 이 주소를 본다. (LocalAppleRestOperationsConfig)

제공하는 것 — 앞의 둘은 Apple 의 실제 경로와 같고, 서버가 그대로 호출한다.

  GET  /auth/keys    JWKS. 아래 키의 공개 JWK 하나.
  POST /auth/token   authorization code 교환. {id_token, refresh_token} 을 돌려준다.
                     client_secret 은 검증하지 않는다. code 에 박아 둔 sub 를 그대로 쓴다.
  GET  /_test/mint   로그인용 id_token + authorization_code 발급. (Apple 에는 없는 경로)

키는 `--key-dir` (기본 build/fake-apple) 에 저장해 재시작해도 같은 키를 쓴다.
매번 새로 만들면 서버의 JWKS 캐시(5분)에 남은 옛 키 때문에 서명 검증이 깨진다.
build/ 는 .gitignore 대상이라 키가 커밋되지 않는다.

사용법:
  scripts/fake-apple.py                      # 127.0.0.1:9099 에 기동
  scripts/fake-apple.py --port 9099
  scripts/fake-apple.py --print-client-key   # APPLE_PRIVATE_KEY 용 더미 P-256 키만 출력

  # 서버 기동에 필요한 더미 키 주입
  export APPLE_PRIVATE_KEY="$(scripts/fake-apple.py --print-client-key)"
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa

REPO_ROOT = Path(__file__).resolve().parent.parent

ISSUER = "https://appleid.apple.com"
KID = "local-apple-key"
TOKEN_LIFETIME = 600  # 초. 서버의 exp 검사(clock skew 60초)를 넉넉히 통과한다.
CODE_PREFIX = "code."


# --------------------------------------------------------------------------- 키


def _b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def load_or_create_keys(key_dir: Path) -> tuple[str, str]:
    """(서명용 RSA PEM, 더미 P-256 PEM) 을 돌려준다. 없으면 만들어 저장한다."""
    key_dir.mkdir(parents=True, exist_ok=True)

    rsa_path = key_dir / "id-token-rsa.pem"
    if not rsa_path.exists():
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        rsa_path.write_bytes(key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ))
        rsa_path.chmod(0o600)

    # AppleClientSecretGenerator 가 기동 시점에 파싱하므로 형식이 맞는 P-256 키가 필요하다.
    # 이 서버는 client_secret 을 검증하지 않으므로 값 자체는 아무 의미가 없다.
    ec_path = key_dir / "client-secret-ec.pem"
    if not ec_path.exists():
        key = ec.generate_private_key(ec.SECP256R1())
        ec_path.write_bytes(key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ))
        ec_path.chmod(0o600)

    return rsa_path.read_text(), ec_path.read_text()


def public_jwks(rsa_pem: str) -> dict:
    public_key = serialization.load_pem_private_key(rsa_pem.encode(), password=None).public_key()
    numbers = public_key.public_numbers()
    return {"keys": [{
        "kty": "RSA",
        "kid": KID,
        "use": "sig",
        "alg": "RS256",
        "n": _b64url(numbers.n.to_bytes((numbers.n.bit_length() + 7) // 8, "big")),
        "e": _b64url(numbers.e.to_bytes((numbers.e.bit_length() + 7) // 8, "big")),
    }]}


# --------------------------------------------------------------------------- 토큰


def mint_id_token(rsa_pem: str, *, sub: str, aud: str, email: str | None, raw_nonce: str | None) -> str:
    now = int(time.time())
    claims = {
        "iss": ISSUER,
        "aud": aud,
        "sub": sub,
        "iat": now,
        "exp": now + TOKEN_LIFETIME,
    }
    if email:
        # AppleOAuthAuthorizationAdapter 는 email 이 없으면 거절한다.
        claims["email"] = email
        claims["email_verified"] = True
        claims["is_private_email"] = True
    if raw_nonce:
        # 앱이 Apple 에 넘기는 것은 sha256(raw_nonce) 의 소문자 hex 다.
        claims["nonce"] = hashlib.sha256(raw_nonce.encode()).hexdigest()
    return jwt.encode(claims, rsa_pem, algorithm="RS256", headers={"kid": KID})


def encode_code(sub: str) -> str:
    """code 에 sub 를 담아 /auth/token 이 상태 없이 같은 sub 를 돌려줄 수 있게 한다.

    교환 응답 id_token 의 sub 가 로그인 id_token 의 sub 와 다르면 서버가
    SUBJECT_MISMATCH 로 로그인을 막는다 (AppleAuthorizationCodeExchangeAdapter.verifySubject).
    """
    return CODE_PREFIX + _b64url(sub.encode())


def decode_code(code: str) -> str | None:
    if not code.startswith(CODE_PREFIX):
        return None
    payload = code[len(CODE_PREFIX):]
    try:
        return base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)).decode()
    except (ValueError, UnicodeDecodeError):
        return None


# --------------------------------------------------------------------------- 서버


class FakeAppleHandler(BaseHTTPRequestHandler):
    rsa_pem: str = ""
    default_email: str = "local@privaterelay.appleid.com"

    def do_GET(self) -> None:  # noqa: N802
        route = urlparse(self.path)
        if route.path == "/auth/keys":
            self._json(200, public_jwks(self.rsa_pem))
        elif route.path == "/_test/mint":
            self._mint(parse_qs(route.query))
        else:
            self._json(404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        route = urlparse(self.path)
        if route.path == "/auth/token":
            length = int(self.headers.get("Content-Length", "0"))
            self._token(parse_qs(self.rfile.read(length).decode()))
        else:
            self._json(404, {"error": "not_found"})

    def _mint(self, query: dict[str, list[str]]) -> None:
        sub = self._one(query, "sub")
        aud = self._one(query, "aud")
        if not sub or not aud:
            self._json(400, {"error": "sub 와 aud 는 필수입니다"})
            return
        id_token = mint_id_token(
            self.rsa_pem,
            sub=sub,
            aud=aud,
            email=self._one(query, "email") or self.default_email,
            raw_nonce=self._one(query, "raw_nonce"),
        )
        self._json(200, {"id_token": id_token, "authorization_code": encode_code(sub)})

    def _token(self, form: dict[str, list[str]]) -> None:
        client_id = self._one(form, "client_id")
        code = self._one(form, "code")
        sub = decode_code(code) if code else None
        if not client_id or sub is None:
            # 진짜 Apple 과 같은 모양으로 거절한다. 서버는 이것을 REJECTED 로 매핑한다.
            self._json(400, {"error": "invalid_grant"})
            return

        # 교환 응답의 id_token 은 aud 가 요청의 client_id 하나여야 하고 sub 가 같아야 한다.
        self._json(200, {
            "access_token": "fake-access-token",
            "token_type": "Bearer",
            "expires_in": 3600,
            "refresh_token": "fake-refresh-token",
            "id_token": mint_id_token(self.rsa_pem, sub=sub, aud=client_id, email=None, raw_nonce=None),
        })

    @staticmethod
    def _one(values: dict[str, list[str]], key: str) -> str | None:
        found = values.get(key)
        return found[0] if found else None

    def _json(self, status: int, body: dict) -> None:
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt: str, *args) -> None:
        # 서버가 실제로 이 스텁을 때렸는지 눈으로 확인하는 것이 이 스크립트의 핵심이다.
        sys.stderr.write("  %s %s\n" % (self.address_string(), fmt % args))


# --------------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(
        description="로컬 전용 가짜 Apple 서버",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__.split("사용법:")[1] if "사용법:" in __doc__ else None,
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9099)
    parser.add_argument("--key-dir", type=Path, default=REPO_ROOT / "build" / "fake-apple")
    parser.add_argument("--email", default="local@privaterelay.appleid.com", help="mint 의 email 기본값")
    parser.add_argument("--print-client-key", action="store_true",
                        help="APPLE_PRIVATE_KEY 에 넣을 더미 P-256 PEM 만 출력하고 끝낸다")
    args = parser.parse_args()

    rsa_pem, ec_pem = load_or_create_keys(args.key_dir)

    if args.print_client_key:
        sys.stdout.write(ec_pem)
        return 0

    FakeAppleHandler.rsa_pem = rsa_pem
    FakeAppleHandler.default_email = args.email

    server = ThreadingHTTPServer((args.host, args.port), FakeAppleHandler)
    print(f"==> 가짜 Apple 서버: http://{args.host}:{args.port}  (키: {args.key_dir})")
    print("    서버는 scripts/run.sh --profile local 로 띄워야 이쪽을 봅니다.")
    print("    중지: Ctrl-C")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n==> 종료")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
