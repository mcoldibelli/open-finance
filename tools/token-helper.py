#!/usr/bin/env python3
"""
Gera JWTs de teste e sobe um endpoint JWKS para o smoke test.
Computa o thumbprint do client.crt da mesma forma que CertificateThumbprint.computeS256 em Java:
  SHA-256 do encoding DER do X.509 -> base64url sem padding.
"""
import hashlib
import json
import os
import sys
import threading
import uuid
from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone
from http.server import HTTPServer, BaseHTTPRequestHandler

import jwt as pyjwt
from cryptography import x509
from cryptography.hazmat.primitives import serialization

KID = "smoke-test-key-1"


def compute_cert_thumbprint(cert_path):
    """RFC 8705 — SHA-256 do DER, base64url sem padding."""
    with open(cert_path, "rb") as f:
        cert = x509.load_pem_x509_certificate(f.read())
    der = cert.public_bytes(serialization.Encoding.DER)
    return urlsafe_b64encode(hashlib.sha256(der).digest()).rstrip(b"=").decode()


def int_to_b64(n, length=None):
    b = n.to_bytes(length or ((n.bit_length() + 7) // 8), byteorder="big")
    return urlsafe_b64encode(b).rstrip(b"=").decode()


def main():
    certs_dir = sys.argv[1]
    output_file = sys.argv[2]

    with open(os.path.join(certs_dir, "as-signing.key"), "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None)

    thumbprint = compute_cert_thumbprint(os.path.join(certs_dir, "client.crt"))

    # JWKS endpoint
    pub = private_key.public_key().public_numbers()
    jwks_json = json.dumps({"keys": [{
        "kty": "RSA", "kid": KID, "use": "sig", "alg": "RS256",
        "n": int_to_b64(pub.n), "e": int_to_b64(pub.e, 3),
    }]})

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path in ("/jwks.json", "/.well-known/jwks.json"):
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(jwks_json.encode())
            else:
                self.send_response(404)
                self.end_headers()

        def log_message(self, *a):
            pass

    server = HTTPServer(("0.0.0.0", 9000), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()

    # Tokens de teste
    now = datetime.now(timezone.utc)

    def make_token(consent_id="consent-smoke-test", cnf_thumb=thumbprint,
                   exp_delta=timedelta(hours=1), jti=None, include_cnf=True, kid=KID):
        payload = {
            "iss": "https://as.localdev.codaline",
            "aud": "https://gateway.localdev.codaline",
            "jti": jti or str(uuid.uuid4()),
            "iat": now, "exp": now + exp_delta,
            "consent_id": consent_id, "cpf": "12345678900", "client_id": "tpp-simulado",
        }
        if include_cnf:
            payload["cnf"] = {"x5t#S256": cnf_thumb}
        return pyjwt.encode(payload, private_key, algorithm="RS256", headers={"kid": kid})

    tokens = {
        "VALID_TOKEN":           make_token(),
        "EXPIRED_TOKEN":         make_token(exp_delta=timedelta(hours=-1)),
        "NO_CNF_TOKEN":          make_token(include_cnf=False),
        "WRONG_THUMB_TOKEN":     make_token(cnf_thumb="AAAA_wrong_thumbprint_BBBB"),
        "UNKNOWN_KID_TOKEN":     make_token(kid="kid-que-nao-existe"),
        "WRONG_CONSENT_TOKEN":   make_token(consent_id="consent-nao-existe"),
        "REVOKED_CONSENT_TOKEN": make_token(consent_id="consent-revoked"),
        "REPLAY_TOKEN":          make_token(jti="replay-jti-smoke"),
        "CLIENT_THUMBPRINT":     thumbprint,
    }
    # Rate limit: precisa de tokens com JTIs diferentes (senao JtiReplayFilter bloqueia)
    for i in range(10):
        tokens[f"RATE_LIMIT_TOKEN_{i}"] = make_token(consent_id="consent-rate-limit")

    with open(output_file, "w") as f:
        for k, v in tokens.items():
            f.write(f'{k}="{v}"\n')

    print("TOKENS_READY", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
