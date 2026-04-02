#!/usr/bin/env bash
# Open Finance Gateway — Smoke Test
# Valida: mTLS, JWT/JWKS, certificate binding, consent, JTI replay, rate limiting, audit trail.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# ── Cores e contadores ──
RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
PASS=0; FAIL=0

pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; PASS=$((PASS + 1)); }
fail() { echo -e "  ${RED}[FAIL]${NC} $1  (esperado=$2, obtido=$3)"; FAIL=$((FAIL + 1)); }

# HTTP client via Python (curl do Windows usa Schannel que nao suporta --cert PEM)
http_code() {
  "$PYTHON" "$SCRIPT_DIR/http-client.py" "$@" 2>/dev/null || echo "000"
}

# ── Env ──
set -a; source .env; set +a
CERTS_DIR="${CERTS_DIR:?Defina CERTS_DIR no .env}"
PYTHON="${PYTHON:-$(command -v python3 2>/dev/null || command -v python 2>/dev/null || true)}"
TOKENS_FILE=$(mktemp)
GATEWAY_LOG=$(mktemp)
PIDS=()

cleanup() {
  echo ""
  echo "Limpando..."
  for pid in "${PIDS[@]}"; do
    # Mata o processo e todos os filhos (Java fica orfao se so matar o Maven wrapper)
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "mingw"* ]]; then
      taskkill //F //T //PID "$pid" >/dev/null 2>&1 || true
    else
      kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
  done
  rm -f "$TOKENS_FILE" "$GATEWAY_LOG"
  echo "Para parar a infra Docker: docker compose down"
}
trap cleanup EXIT

echo ""
echo "======================================================"
echo "   Open Finance Gateway — Smoke Test"
echo "======================================================"

# ── 1. Pre-requisitos ──
echo ""
echo "[1/7] Pre-requisitos..."
for cmd in docker "$PYTHON" openssl java; do
  command -v "$cmd" >/dev/null 2>&1 || { echo -e "  ${RED}ERRO:${NC} '$cmd' nao encontrado"; exit 1; }
  echo -e "  ${GREEN}[OK]${NC} $cmd"
done

# ── 2. PKI ──
echo ""
echo "[2/7] Certificados..."
if [[ ! -f "$CERTS_DIR/server.p12" || ! -f "$CERTS_DIR/client.crt" || ! -f "$CERTS_DIR/as-signing.key" ]]; then
  bash "$SCRIPT_DIR/setup-pki.sh"
fi
echo -e "  ${GREEN}[OK]${NC} PKI pronta"

# ── 3. Docker (infra + accounts stub) ──
echo ""
echo "[3/7] Infraestrutura Docker..."
docker compose up -d redis kafka config-server accounts-service 2>/dev/null

echo "  Aguardando health checks..."
for svc in redis kafka config-server; do
  for i in $(seq 1 40); do
    if docker compose ps "$svc" 2>/dev/null | grep -q "(healthy)"; then
      echo -e "  ${GREEN}[OK]${NC} $svc"
      break
    fi
    if [[ $i -eq 40 ]]; then echo -e "  ${RED}ERRO:${NC} $svc nao ficou healthy"; exit 1; fi
    sleep 3
  done
done
sleep 5
echo -e "  ${GREEN}[OK]${NC} accounts-service"

# ── 4. JWKS server + tokens ──
echo ""
echo "[4/7] JWKS server + tokens..."
"$PYTHON" "$SCRIPT_DIR/token-helper.py" "$CERTS_DIR" "$TOKENS_FILE" &
PIDS+=($!)

for i in $(seq 1 20); do
  [[ -s "$TOKENS_FILE" ]] && break
  if [[ $i -eq 20 ]]; then echo -e "  ${RED}ERRO:${NC} tokens nao gerados"; exit 1; fi
  sleep 0.5
done
source "$TOKENS_FILE"
echo -e "  ${GREEN}[OK]${NC} JWKS em http://localhost:9000/jwks.json"
echo -e "  ${GREEN}[OK]${NC} $(grep -c '=' "$TOKENS_FILE") variaveis exportadas (9 tokens + thumbprint)"

# ── 5. Seed Redis ──
echo ""
echo "[5/7] Seed Redis (consents)..."
RCLI="docker exec redis redis-cli -a $REDIS_PASSWORD --no-auth-warning"

$RCLI HSET consent:consent-smoke-test \
  consent_id consent-smoke-test status AUTHORISED \
  permissions ACCOUNTS_READ,ACCOUNTS_BALANCES_READ \
  cpf 12345678900 client_id tpp-simulado >/dev/null
$RCLI EXPIRE consent:consent-smoke-test 3600 >/dev/null

$RCLI HSET consent:consent-revoked \
  consent_id consent-revoked status REVOKED \
  permissions ACCOUNTS_READ cpf 12345678900 client_id tpp-simulado >/dev/null
$RCLI EXPIRE consent:consent-revoked 3600 >/dev/null

$RCLI HSET consent:consent-rate-limit \
  consent_id consent-rate-limit status AUTHORISED \
  permissions ACCOUNTS_READ,ACCOUNTS_BALANCES_READ \
  cpf 12345678900 client_id tpp-simulado >/dev/null
$RCLI EXPIRE consent:consent-rate-limit 3600 >/dev/null

# Limpa JTIs de execucoes anteriores para garantir estado limpo
$RCLI KEYS "jti:*" 2>/dev/null | while read -r key; do $RCLI DEL "$key" >/dev/null; done

# Garante que topico audit.events existe no Kafka (// evita MSYS path conversion)
docker exec kafka-openfinance //opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic audit.events --partitions 1 --replication-factor 1 >/dev/null 2>&1 || true

echo -e "  ${GREEN}[OK]${NC} 3 consents + JTI cache limpo + topico audit.events"

# ── 6. Gateway (mTLS habilitado) ──
echo ""
echo "[6/7] Iniciando gateway com mTLS..."

./mvnw spring-boot:run -pl gateway-service -q \
  "-Dspring-boot.run.arguments=--jwks.url=http://localhost:9000/jwks.json,--gateway.rate-limit.burst-capacity=2,--gateway.rate-limit.replenish-rate=1" \
  > "$GATEWAY_LOG" 2>&1 &
PIDS+=($!)

GW="https://localhost:8080"
CERT_ARGS="--cert $CERTS_DIR/client.crt --key $CERTS_DIR/client.key --cacert $CERTS_DIR/ca.crt"

echo "  Aguardando $GW (pode levar ~30s)..."
for i in $(seq 1 60); do
  if http_code $CERT_ARGS "$GW/actuator/health" 2>/dev/null | grep -q "200"; then
    echo -e "  ${GREEN}[OK]${NC} Gateway HTTPS pronto (mTLS ativo, porta 8080)"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo -e "  ${RED}ERRO:${NC} gateway nao iniciou. Log:"
    tail -20 "$GATEWAY_LOG"
    exit 1
  fi
  sleep 3
done

# ══════════════════════════════════════════════════
# ── 7. MATRIZ DE TESTES ──
# ══════════════════════════════════════════════════
echo ""
echo "[7/7] Executando testes..."
URI="/open-banking/accounts/v2"

# ── mTLS (camada TLS) ──
echo ""
echo "  -- mTLS (camada TLS) --"

# T1: Sem certificado -> handshake rejeitado (retorna 000)
code=$(http_code --cacert "$CERTS_DIR/ca.crt" "$GW$URI")
if [[ "$code" == "000" ]]; then pass "Sem certificado cliente            -> TLS rejeitado"
else fail "Sem certificado cliente" "TLS rejeitado" "HTTP $code"; fi

# T2: Cert nao assinado pela CA -> handshake rejeitado
code=$(http_code --cert "$CERTS_DIR/untrusted-client.crt" --key "$CERTS_DIR/untrusted-client.key" \
  --cacert "$CERTS_DIR/ca.crt" "$GW$URI")
if [[ "$code" == "000" ]]; then pass "Certificado nao-confiavel          -> TLS rejeitado"
else fail "Certificado nao-confiavel" "TLS rejeitado" "HTTP $code"; fi

# ── JWT / JWKS ──
echo ""
echo "  -- JWT / JWKS --"

# T3: Sem Bearer token
code=$(http_code $CERT_ARGS "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Sem Bearer token                   -> 401"
else fail "Sem Bearer token" "401" "$code"; fi

# T4: Token expirado
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $EXPIRED_TOKEN" "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Token expirado                     -> 401"
else fail "Token expirado" "401" "$code"; fi

# T5: kid desconhecido (JWKS refresh -> still miss)
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $UNKNOWN_KID_TOKEN" "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Token com kid desconhecido         -> 401"
else fail "Token com kid desconhecido" "401" "$code"; fi

# T6: Token sem claim cnf
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $NO_CNF_TOKEN" "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Token sem claim cnf                -> 401"
else fail "Token sem claim cnf" "401" "$code"; fi

# ── Certificate Binding (RFC 8705) ──
echo ""
echo "  -- Certificate Binding (RFC 8705) --"

# T7: cnf.x5t#S256 != thumbprint do cert apresentado
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $WRONG_THUMB_TOKEN" "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Thumbprint != cnf.x5t#S256         -> 401"
else fail "Thumbprint != cnf.x5t#S256" "401" "$code"; fi

# ── Consent ──
echo ""
echo "  -- Consent --"

# T8: AUTHORISED
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $VALID_TOKEN" "$GW$URI")
if [[ "$code" == "200" ]]; then pass "Consent AUTHORISED                 -> 200"
else fail "Consent AUTHORISED" "200" "$code"; fi

# T9: REVOKED
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $REVOKED_CONSENT_TOKEN" "$GW$URI")
if [[ "$code" == "403" ]]; then pass "Consent REVOKED                    -> 403"
else fail "Consent REVOKED" "403" "$code"; fi

# T10: Inexistente
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $WRONG_CONSENT_TOKEN" "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Consent inexistente                -> 401"
else fail "Consent inexistente" "401" "$code"; fi

# ── JTI Replay ──
echo ""
echo "  -- JTI Replay --"

# T11: Primeiro uso
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $REPLAY_TOKEN" "$GW$URI")
if [[ "$code" == "200" ]]; then pass "Primeiro uso do JTI                -> 200"
else fail "Primeiro uso do JTI" "200" "$code"; fi

# T12: Replay (mesmo token)
code=$(http_code $CERT_ARGS -H "Authorization: Bearer $REPLAY_TOKEN" "$GW$URI")
if [[ "$code" == "401" ]]; then pass "Replay (mesmo JTI)                 -> 401"
else fail "Replay (mesmo JTI)" "401" "$code"; fi

# ── Rate Limiting ──
echo ""
echo "  -- Rate Limiting --"

# T13: Burst acima do capacity (burst=2, envia 10 rapido no mesmo processo Python)
GOT_429=$("$PYTHON" -c "
import ssl, urllib.request, uuid, hashlib
from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone
from cryptography import x509
from cryptography.hazmat.primitives import serialization
import jwt as pyjwt

ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
ctx.load_cert_chain('$CERTS_DIR/client.crt', '$CERTS_DIR/client.key')
ctx.load_verify_locations('$CERTS_DIR/ca.crt')
with open('$CERTS_DIR/as-signing.key', 'rb') as f:
    pk = serialization.load_pem_private_key(f.read(), password=None)
with open('$CERTS_DIR/client.crt', 'rb') as f:
    cert = x509.load_pem_x509_certificate(f.read())
thumb = urlsafe_b64encode(hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).digest()).rstrip(b'=').decode()
now = datetime.now(timezone.utc)
for i in range(10):
    tok = pyjwt.encode({'iss':'https://as.localdev.codaline','aud':'https://gateway.localdev.codaline',
        'jti':str(uuid.uuid4()),'iat':now,'exp':now+timedelta(hours=1),
        'consent_id':'consent-rate-limit','cpf':'12345678900','client_id':'tpp-simulado',
        'cnf':{'x5t#S256':thumb}}, pk, algorithm='RS256', headers={'kid':'smoke-test-key-1'})
    req = urllib.request.Request('$GW$URI', headers={'Authorization': f'Bearer {tok}'})
    try:
        r = urllib.request.urlopen(req, context=ctx, timeout=5)
        code = r.status
    except urllib.error.HTTPError as e:
        code = e.code
    except:
        code = 0
    if code == 429:
        print('true')
        break
else:
    print('false')
" 2>/dev/null)
if [[ "$GOT_429" == "true" ]]; then pass "Burst acima do capacity            -> 429"
else fail "Burst acima do capacity" "429" "nenhum 429 em 10 requests"; fi

# ── Audit Trail (BACEN 4.658) ──
echo ""
echo "  -- Audit Trail (BACEN 4.658) --"

# T14: Eventos no Kafka
AUDIT_COUNT=$(docker exec kafka-openfinance //opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic audit.events \
  --from-beginning --timeout-ms 5000 2>/dev/null | grep -c "MTLS_VALIDATION" || true)
AUDIT_COUNT="${AUDIT_COUNT:-0}"
if [[ "$AUDIT_COUNT" -gt 0 ]]; then
  pass "Eventos em audit.events            -> $AUDIT_COUNT eventos"
else
  fail "Eventos em audit.events" ">0" "$AUDIT_COUNT"
fi

# ══════════════════════════════════════════════════
# ── Relatorio ──
# ══════════════════════════════════════════════════
TOTAL=$((PASS + FAIL))
echo ""
echo "------------------------------------------------------"
if [[ $FAIL -eq 0 ]]; then
  echo -e "${GREEN}  RESULTADO: $PASS/$TOTAL PASSED${NC}"
else
  echo -e "${RED}  RESULTADO: $PASS/$TOTAL PASSED, $FAIL FAILED${NC}"
fi
echo "------------------------------------------------------"
echo ""

[[ $FAIL -eq 0 ]] || exit 1
