#!/usr/bin/env python3
"""
Sign the Apple client secret JWT (ES256) with nothing but openssl.

PyJWT/cryptography are not installed and this should not need them: the whole
job is a base64url header, a base64url payload, and one ECDSA signature — and
`openssl dgst -sign` emits DER, which ES256 does not want. The only real work
below is turning that DER (SEQUENCE{INTEGER r, INTEGER s}) into the raw 64-byte
r‖s JOSE form.
"""
import base64, json, subprocess, sys, time, tempfile, os

def b64u(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

def der_to_raw(der: bytes, size: int = 32) -> bytes:
    """SEQUENCE { INTEGER r, INTEGER s } → r‖s, each left-padded to `size`."""
    assert der[0] == 0x30, "not a DER SEQUENCE"
    # Skip the sequence header (long form length when the high bit is set).
    i = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)
    out = b""
    for _ in range(2):
        assert der[i] == 0x02, "expected an INTEGER"
        length = der[i + 1]
        value = der[i + 2 : i + 2 + length].lstrip(b"\x00")
        out += value.rjust(size, b"\x00")
        i += 2 + length
    return out

def sign(p8_path: str, team_id: str, key_id: str, services_id: str, days: int = 180) -> str:
    header = {"alg": "ES256", "kid": key_id}
    now = int(time.time())
    payload = {
        "iss": team_id,
        "iat": now,
        # Apple caps this at 6 months (15777000s). Anything longer is rejected.
        "exp": now + days * 86400,
        "aud": "https://appleid.apple.com",
        "sub": services_id,
    }
    signing_input = f"{b64u(json.dumps(header, separators=(',', ':')).encode())}." \
                    f"{b64u(json.dumps(payload, separators=(',', ':')).encode())}"
    with tempfile.NamedTemporaryFile(delete=False) as f:
        f.write(signing_input.encode())
        tmp = f.name
    try:
        der = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", p8_path, tmp],
            capture_output=True, check=True,
        ).stdout
    finally:
        os.unlink(tmp)
    return f"{signing_input}.{b64u(der_to_raw(der))}"

if __name__ == "__main__":
    if len(sys.argv) != 5:
        print("用法: apple_secret.py <AuthKey.p8> <TeamID> <KeyID> <ServicesID>")
        raise SystemExit(2)
    print(sign(*sys.argv[1:]))
