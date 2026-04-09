TLS/mTLS soubory očekávané v `docker-compose.yaml`:

- RabbitMQ serverové certifikáty (mount do `/etc/rabbitmq/certs`):
  - `ops/rabbitmq/certs/ca.crt`
  - `ops/rabbitmq/certs/server.crt`
  - `ops/rabbitmq/certs/server.key`

- Worker klientské certifikáty (mount do `/certs`):
  - `ops/worker/certs/ca.crt`
  - `ops/worker/certs/client.crt`
  - `ops/worker/certs/client.key`

- API Java keystores (pro Spring AMQP SSL/mTLS):
  - `ops/api/certs/api-truststore.p12`
  - `ops/api/certs/api-keystore.p12`

Poznámky:
- `server.crt` musí obsahovat SAN/DNS pro `rabbitmq` (hostname Docker služby).
- Privátní klíče nikdy neukládejte do gitu.

## Dev setup: generování TLS/mTLS certifikátů

Spusťte z kořene repozitáře:

```bash
mkdir -p ops/rabbitmq/certs ops/worker/certs ops/api/certs .tmp-certs
cd .tmp-certs
```

### 1) Vytvoření lokální CA

```bash
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/C=CZ/O=AutomaticKYC/CN=automatic-kyc-ca" \
  -out ca.crt
```

### 2) Vytvoření RabbitMQ server certifikátu

`server.crt` musí obsahovat SAN `DNS:rabbitmq`, protože worker se v Docker síti připojuje na hostname `rabbitmq`.

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

### 3) Vytvoření worker klientského certifikátu (mTLS)

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

### 4) Kopie souborů do očekávaných cest

```bash
cp ca.crt ../ops/rabbitmq/certs/ca.crt
cp server.crt ../ops/rabbitmq/certs/server.crt
cp server.key ../ops/rabbitmq/certs/server.key

cp ca.crt ../ops/worker/certs/ca.crt
cp client.crt ../ops/worker/certs/client.crt
cp client.key ../ops/worker/certs/client.key
```

### 5) Zabezpečení klíčů a úklid dočasných souborů

```bash
chmod 600 ../ops/rabbitmq/certs/server.key ../ops/worker/certs/client.key
cd ..
rm -rf .tmp-certs
```

### 6) Spuštění stacku

```bash
docker compose up -d --build
```

Volitelná kontrola:

```bash
docker compose logs -f rabbitmq worker
```

## API mTLS setup (Spring Boot)

Spring AMQP SSL očekává Java keystore/truststore soubory. Vygenerujte je ze stejné CA a klientského certifikátu jako výše.

Spusťte z kořene repozitáře:

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

Nastavte API env proměnné:

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
