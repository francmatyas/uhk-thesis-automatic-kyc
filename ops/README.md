TLS/mTLS files expected by `docker-compose.yaml`:

- RabbitMQ server-side certs (mounted to `/etc/rabbitmq/certs`):
  - `ops/rabbitmq/certs/ca.crt`
  - `ops/rabbitmq/certs/server.crt`
  - `ops/rabbitmq/certs/server.key`

- Worker client-side certs (mounted to `/certs`):
  - `ops/worker/certs/ca.crt`
  - `ops/worker/certs/client.crt`
  - `ops/worker/certs/client.key`

- API Java keystores (for Spring AMQP SSL/mTLS):
  - `ops/api/certs/api-truststore.p12`
  - `ops/api/certs/api-keystore.p12`

Notes:
- `server.crt` must have SAN/DNS for `rabbitmq` (the Docker service hostname).
- Keep private keys out of git.

## Dev setup: generate TLS/mTLS certificates

Run from repository root:

```bash
mkdir -p ops/rabbitmq/certs ops/worker/certs ops/api/certs .tmp-certs
cd .tmp-certs
```

### 1) Create local CA

```bash
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/C=CZ/O=AutomaticKYC/CN=automatic-kyc-ca" \
  -out ca.crt
```

### 2) Create RabbitMQ server certificate

`server.crt` must include SAN `DNS:rabbitmq` because workers connect to hostname `rabbitmq` in Docker network.

```bash
cat > server.cnf <<'EOF'
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
C = CZ
O = AutomaticKYC
CN = rabbitmq

[req_ext]
subjectAltName = @alt_names
extendedKeyUsage = serverAuth

[alt_names]
DNS.1 = rabbitmq
DNS.2 = localhost
EOF

openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -config server.cnf
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 825 -sha256 -extensions req_ext -extfile server.cnf
```

### 3) Create worker client certificate (mTLS)

```bash
cat > client.cnf <<'EOF'
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
C = CZ
O = AutomaticKYC
CN = kyc-worker

[req_ext]
extendedKeyUsage = clientAuth
EOF

openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr -config client.cnf
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client.crt -days 825 -sha256 -extensions req_ext -extfile client.cnf
```

### 4) Copy files to expected paths

```bash
cp ca.crt ../ops/rabbitmq/certs/ca.crt
cp server.crt ../ops/rabbitmq/certs/server.crt
cp server.key ../ops/rabbitmq/certs/server.key

cp ca.crt ../ops/worker/certs/ca.crt
cp client.crt ../ops/worker/certs/client.crt
cp client.key ../ops/worker/certs/client.key
```

### 5) Secure keys and clean temporary files

```bash
chmod 600 ../ops/rabbitmq/certs/server.key ../ops/worker/certs/client.key
cd ..
rm -rf .tmp-certs
```

### 6) Start stack

```bash
docker compose up -d --build
```

Optional check:

```bash
docker compose logs -f rabbitmq worker
```

## API mTLS setup (Spring Boot)

Spring AMQP SSL expects Java keystore/truststore files. Generate them from the
same CA/client certs used above.

From repository root:

```bash
keytool -importcert -noprompt \
  -alias rabbitmq-ca \
  -file ops/rabbitmq/certs/ca.crt \
  -keystore ops/api/certs/api-truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit

openssl pkcs12 -export \
  -in ops/worker/certs/client.crt \
  -inkey ops/worker/certs/client.key \
  -certfile ops/rabbitmq/certs/ca.crt \
  -name api-client \
  -out ops/api/certs/api-keystore.p12 \
  -passout pass:changeit
```

Set API env vars:

```bash
RABBITMQ_PORT=5671
RABBITMQ_SSL_ENABLED=true
RABBITMQ_SSL_VERIFY_HOSTNAME=true
RABBITMQ_TRUSTSTORE_PATH=ops/api/certs/api-truststore.p12
RABBITMQ_TRUSTSTORE_PASSWORD=changeit
RABBITMQ_TRUSTSTORE_TYPE=PKCS12
RABBITMQ_KEYSTORE_PATH=ops/api/certs/api-keystore.p12
RABBITMQ_KEYSTORE_PASSWORD=changeit
RABBITMQ_KEYSTORE_TYPE=PKCS12
```
