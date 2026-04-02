"""
Gateway E2E manual test helper.

Usage:
  1. Ensure Redis, Kafka, Config Server are running (docker compose up redis kafka config-server -d)
  2. Run: python tools/gateway-e2e.py
  3. In another terminal, start the gateway:
     export $(grep -v '^#' .env | xargs)
     ./mvnw spring-boot:run -pl gateway-service \
       -Dspring-boot.run.arguments="--server.ssl.enabled=false --gateway.trust-proxy-headers=true --jwks.url=http://localhost:9000/jwks.json"
  4. Run the curl commands printed by this script
"""

import hashlib
import json
import os
import sys
import threading
import time
import uuid
from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone
from http.server import HTTPServer, SimpleHTTPRequestHandler

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

# --- Key generation ---

private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
public_key = private_key.public_key()
public_numbers = public_key.public_numbers()

KID = "e2e-test-key-1"
THUMBPRINT = hashlib.sha256(b"fake-client-cert-bytes").hexdigest()

def int_to_b64(n, length=None):
    b = n.to_bytes(length or ((n.bit_length() + 7) // 8), byteorder="big")
    return urlsafe_b64encode(b).rstrip(b"=").decode()

jwks = {
    "keys": [{
        "kty": "RSA",
        "kid": KID,
        "use": "sig",
        "alg": "RS256",
        "n": int_to_b64(public_numbers.n),
        "e": int_to_b64(public_numbers.e, 3),
    }]
}

JWKS_JSON = json.dumps(jwks, indent=2)

# --- JWKS HTTP server ---

class JwksHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/jwks.json", "/.well-known/jwks.json"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(JWKS_JSON.encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass  # silence logs

def start_jwks_server():
    server = HTTPServer(("0.0.0.0", 9000), JwksHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server

# --- JWT generation ---

def make_token(consent_id="consent-e2e-test", cpf="12345678900", client_id="tpp-simulado",
               thumbprint=THUMBPRINT, exp_minutes=60, jti=None):
    now = datetime.now(timezone.utc)
    payload = {
        "iss": "https://as.localdev.codaline",
        "aud": "https://gateway.localdev.codaline",
        "jti": jti or str(uuid.uuid4()),
        "iat": now,
        "exp": now + timedelta(minutes=exp_minutes),
        "consent_id": consent_id,
        "cpf": cpf,
        "client_id": client_id,
        "cnf": {"x5t#S256": thumbprint},
    }
    return jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": KID})

# --- Redis consent seed ---

def seed_consent_redis(password):
    import subprocess
    commands = [
        f"HSET consent:consent-e2e-test consent_id consent-e2e-test status AUTHORISED "
        f"permissions ACCOUNTS_READ,ACCOUNTS_BALANCES_READ cpf 12345678900 client_id tpp-simulado",
        "EXPIRE consent:consent-e2e-test 3600",
        f"HSET consent:consent-revoked consent_id consent-revoked status REVOKED "
        f"permissions ACCOUNTS_READ cpf 12345678900 client_id tpp-simulado",
        "EXPIRE consent:consent-revoked 3600",
    ]
    for cmd in commands:
        subprocess.run(
            ["docker", "exec", "redis", "redis-cli", "-a", password, *cmd.split()],
            capture_output=True
        )

# --- Main ---

if __name__ == "__main__":
    redis_password = os.environ.get("REDIS_PASSWORD", "")
    if not redis_password:
        # try reading from .env
        env_path = os.path.join(os.path.dirname(__file__), "..", ".env")
        if os.path.exists(env_path):
            for line in open(env_path):
                if line.startswith("REDIS_PASSWORD="):
                    redis_password = line.strip().split("=", 1)[1]

    if not redis_password:
        print("ERROR: REDIS_PASSWORD not found in env or .env file")
        sys.exit(1)

    print("=== Gateway E2E Test Helper ===\n")

    # 1. Start JWKS server
    start_jwks_server()
    print("[OK] JWKS server running on http://localhost:9000/jwks.json")

    # 2. Seed Redis consents
    seed_consent_redis(redis_password)
    print("[OK] Consents seeded in Redis (consent-e2e-test=AUTHORISED, consent-revoked=REVOKED)\n")

    # 3. Generate tokens
    valid_token = make_token()
    expired_token = make_token(exp_minutes=-1)
    no_cnf_token = jwt.encode({
        "iss": "https://as.localdev.codaline",
        "aud": "https://gateway.localdev.codaline",
        "jti": str(uuid.uuid4()),
        "iat": datetime.now(timezone.utc),
        "exp": datetime.now(timezone.utc) + timedelta(hours=1),
        "consent_id": "consent-e2e-test",
    }, private_key, algorithm="RS256", headers={"kid": KID})
    wrong_consent_token = make_token(consent_id="consent-nao-existe")
    revoked_token = make_token(consent_id="consent-revoked")
    replay_token = make_token(jti="replay-jti-fixed")

    GW = "http://localhost:8080"
    URI = "/open-banking/accounts/v2"

    print("=== Start gateway in another terminal: ===")
    print(f"  export $(grep -v '^#' .env | xargs)")
    print(f"  ./mvnw spring-boot:run -pl gateway-service \\")
    print(f'    -Dspring-boot.run.arguments="--server.ssl.enabled=false --gateway.trust-proxy-headers=true --jwks.url=http://localhost:9000/jwks.json"')
    print()
    print("=== Then run these tests (press Enter after starting gateway): ===")
    input()

    tests = [
        ("1. No token -> 401",
         f'curl -s -o /dev/null -w "%{{http_code}}" {GW}{URI}',
         "401"),

        ("2. No cnf claim -> 401",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {no_cnf_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "401"),

        ("3. Expired token -> 401",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {expired_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "401"),

        ("4. Non-existent consent -> 401",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {wrong_consent_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "401"),

        ("5. Revoked consent -> 403",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {revoked_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "403"),

        ("6. Valid token + AUTHORISED consent -> 200/502",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {valid_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "200 or 502"),

        ("7. JTI replay (first) -> 200/502",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {replay_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "200 or 502"),

        ("8. JTI replay (second = same token) -> 401",
         f'curl -s -o /dev/null -w "%{{http_code}}" -H "Authorization: Bearer {replay_token}" '
         f'-H "X-Cert-Thumbprint: {THUMBPRINT}" {GW}{URI}',
         "401"),
    ]

    print("=== Running tests ===\n")
    for name, cmd, expected in tests:
        result = os.popen(cmd).read().strip()
        ok = result in expected.split(" or ")
        status = "PASS" if ok else "FAIL"
        print(f"  [{status}] {name}")
        print(f"         expected={expected}  got={result}")
        if not ok:
            # show full response for debugging
            debug_cmd = cmd.replace('-o /dev/null -w "%{http_code}"', '-v')
            print(f"         debug: {debug_cmd}")
        print()

    print("=== Audit events (check in Kafka): ===")
    print(f'  docker exec kafka-openfinance //opt/kafka/bin/kafka-console-consumer.sh \\')
    print(f'    --bootstrap-server localhost:9092 --topic audit.events --from-beginning --timeout-ms 5000')
