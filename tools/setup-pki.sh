#!/usr/bin/env bash
# Gera toda a PKI para testes mTLS do Open Finance Gateway.
# Le senhas do .env. Idempotente — se certs existem, nao regera.
set -euo pipefail
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

[[ -f "$ENV_FILE" ]] || { echo "ERRO: .env nao encontrado em $PROJECT_DIR"; exit 1; }

set -a; source "$ENV_FILE"; set +a

CERTS_DIR="${CERTS_DIR:?Defina CERTS_DIR no .env}"

# keytool necessario para truststore Java — detecta via java.home
KEYTOOL="$(command -v keytool 2>/dev/null || true)"
if [[ -z "$KEYTOOL" ]]; then
  JAVA_HOME_DETECTED="$( (java -XshowSettings 2>&1 || true) | grep 'java.home' | cut -d= -f2 | tr -d ' \r' | tr '\\' '/')"
  if [[ -n "$JAVA_HOME_DETECTED" ]]; then
    CANDIDATE="$JAVA_HOME_DETECTED/bin/keytool"
    [[ -f "$CANDIDATE" || -f "${CANDIDATE}.exe" ]] && KEYTOOL="$CANDIDATE"
  fi
fi
[[ -n "$KEYTOOL" ]] || { echo "ERRO: keytool nao encontrado. Adicione o JDK ao PATH."; exit 1; }

if [[ -f "$CERTS_DIR/ca.crt" && -f "$CERTS_DIR/server.p12" && \
      -f "$CERTS_DIR/client.crt" && -f "$CERTS_DIR/truststore.p12" && \
      -f "$CERTS_DIR/as-signing.key" ]]; then
  echo "[OK] PKI ja existe em $CERTS_DIR"
  exit 0
fi

mkdir -p "$CERTS_DIR"

echo "=== Gerando PKI para Open Finance Gateway ==="
echo "    Diretorio: $CERTS_DIR"
echo ""

# 1. CA root
echo "[1/6] CA root..."
openssl genrsa -out "$CERTS_DIR/ca.key" 2048 2>/dev/null
openssl req -new -x509 -days 365 -key "$CERTS_DIR/ca.key" -out "$CERTS_DIR/ca.crt" \
  -subj "/CN=Open Finance CA/O=Codaline/C=BR"

# 2. Certificado do servidor (Gateway)
echo "[2/6] Certificado do servidor..."
openssl genrsa -out "$CERTS_DIR/server.key" 2048 2>/dev/null
openssl req -new -key "$CERTS_DIR/server.key" -out "$CERTS_DIR/server.csr" \
  -subj "/CN=localhost/O=Codaline/C=BR"
openssl x509 -req -days 365 -in "$CERTS_DIR/server.csr" \
  -CA "$CERTS_DIR/ca.crt" -CAkey "$CERTS_DIR/ca.key" -CAcreateserial \
  -out "$CERTS_DIR/server.crt" 2>/dev/null
openssl pkcs12 -export -in "$CERTS_DIR/server.crt" -inkey "$CERTS_DIR/server.key" \
  -out "$CERTS_DIR/server.p12" -name gateway -passout "pass:$SSL_KEYSTORE_PASSWORD"

# 3. Certificado do cliente (TPP confiavel, assinado pela CA)
echo "[3/6] Certificado do cliente (TPP)..."
openssl genrsa -out "$CERTS_DIR/client.key" 2048 2>/dev/null
openssl req -new -key "$CERTS_DIR/client.key" -out "$CERTS_DIR/client.csr" \
  -subj "/CN=tpp-simulado/O=TPP Test/C=BR"
openssl x509 -req -days 365 -in "$CERTS_DIR/client.csr" \
  -CA "$CERTS_DIR/ca.crt" -CAkey "$CERTS_DIR/ca.key" -CAcreateserial \
  -out "$CERTS_DIR/client.crt" 2>/dev/null

# 4. Certificado nao-confiavel (self-signed, NAO assinado pela CA — teste negativo)
echo "[4/6] Certificado nao-confiavel (teste negativo)..."
openssl genrsa -out "$CERTS_DIR/untrusted-client.key" 2048 2>/dev/null
openssl req -new -x509 -days 365 -key "$CERTS_DIR/untrusted-client.key" \
  -out "$CERTS_DIR/untrusted-client.crt" \
  -subj "/CN=evil-tpp/O=Untrusted/C=BR"

# 5. Truststore (contem apenas a CA — gateway confia so em certs assinados por ela)
echo "[5/6] Truststore..."
"$KEYTOOL" -import -file "$CERTS_DIR/ca.crt" -alias ca-root \
  -keystore "$CERTS_DIR/truststore.p12" -storetype PKCS12 \
  -storepass "$SSL_TRUSTSTORE_PASSWORD" -noprompt

# 6. Par de chaves do Authorization Server (assina os JWTs de teste)
echo "[6/6] Chave de assinatura JWT (AS)..."
openssl genrsa -out "$CERTS_DIR/as-signing.key" 2048 2>/dev/null
openssl rsa -in "$CERTS_DIR/as-signing.key" -pubout \
  -out "$CERTS_DIR/as-signing.pub" 2>/dev/null

rm -f "$CERTS_DIR"/*.csr "$CERTS_DIR"/*.srl

echo ""
echo "[OK] PKI gerada com sucesso"
echo "     CA:            ca.crt / ca.key"
echo "     Servidor:      server.p12"
echo "     Cliente:       client.crt / client.key"
echo "     Nao-confiavel: untrusted-client.crt / untrusted-client.key"
echo "     Truststore:    truststore.p12"
echo "     JWT (AS):      as-signing.key / as-signing.pub"
