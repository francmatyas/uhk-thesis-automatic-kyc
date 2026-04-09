# Init systému (lokální vývoj)

Tento návod popisuje spuštění celého KYC stacku v Dockeru včetně TLS/mTLS mezi `api`, `worker` a `rabbitmq`.

## 1. Požadavky
- Docker + Docker Compose
- `openssl`
- JDK s nástrojem `keytool` (pro `api` truststore/keystore)

Volitelně:
- `curl` pro rychlé healthchecky

## 2. Konfigurace prostředí
V kořeni repozitáře:

```bash
cp .env.example .env
```

Pro lokální běh doporučené minimum v `.env`:

```dotenv
API_HOST=api.localhost
ADMIN_HOST=admin.localhost
VERIFY_HOST=verify.localhost
MINIO_HOST=minio.localhost
MINIO_CONSOLE_HOST=minio-console.localhost

KYC_FLOW_BASE_URL=http://verify.localhost/v
CORS_ALLOWED_ORIGINS=http://admin.localhost,http://verify.localhost

VITE_ADMIN_API_URL=http://api.localhost
VITE_ADMIN_WS_URL=ws://api.localhost
VITE_VERIFY_API_BASE_URL=http://api.localhost

S3_ENDPOINT=http://minio:9000
S3_WORKER_PRESIGN_ENDPOINT=http://minio:9000
S3_PUBLIC_BASE_URL=http://minio.localhost/automatic-kyc-localhost
```

Pokud chcete bootstrap admin účet při startu API:

```dotenv
APP_BOOTSTRAP_ENABLED=true
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
APP_BOOTSTRAP_ADMIN_PASSWORD=change-me-please
```

## 3. TLS/mTLS certifikáty a Java keystores
Postup je v [ops/README.md](../ops/README.md). Stručně:
1. vytvořit lokální CA,
2. vystavit server certifikát pro RabbitMQ (`DNS:rabbitmq`),
3. vystavit klientský certifikát pro worker,
4. vytvořit `api` truststore/keystore (`PKCS12`),
5. uložit soubory do:
- `ops/rabbitmq/certs/*`
- `ops/worker/certs/*`
- `ops/api/certs/*`

## 4. Start stacku

```bash
docker compose up -d --build
```

Doporučená kontrola logů:

```bash
docker compose logs -f api worker rabbitmq
```

## 5. Ověření dostupnosti služeb
- Admin UI: `http://admin.localhost`
- Verify UI: `http://verify.localhost`
- API přes Traefik: `http://api.localhost`
- RabbitMQ management: `http://localhost:15672`
- Traefik dashboard: `http://localhost:8088`
- MinIO konzole: `http://minio-console.localhost` (nebo port `9001`, podle host nastavení)

## 6. První průchod verifikací

### 6.1 Přihlášení do admin a výběr tenantu
1. Otevřete `http://admin.localhost` a přihlaste se.
2. Přepněte se do tenant scope.
3. Zkontrolujte, že URL odpovídá tvaru:
```text
/t/{tenantSlug}
```

### 6.2 Nastavení journey template
1. Otevřete:
```text
http://admin.localhost/t/{tenantSlug}/journey-templates
```
2. Vytvořte nový template (`New Template`) nebo upravte existující.
3. Nastavte:
- `status = ACTIVE`
- `allowedDocumentTypes`: alespoň `CZECH_ID` nebo `PASSPORT`
- `optionalSteps` dle potřeby testu:
- `EMAIL_VERIFICATION`
- `PHONE_VERIFICATION`
- `AML_QUESTIONNAIRE`
4. Uložte template.
5. Z detailu si poznamenejte `journeyTemplateId` (je v URL):
```text
/t/{tenantSlug}/journey-templates/{journeyTemplateId}
```

### 6.3 Vytvoření API klíče
1. Otevřete:
```text
http://admin.localhost/t/{tenantSlug}/api-keys
```
2. Vytvořte nový klíč (`New API Key`) se stavem `ACTIVE`.
3. Uložte si hodnoty:
- `publicKey` (`pk_...`)
- `secret` (`sk_...`, zobrazí se pouze jednou)

### 6.4 Vytvoření verifikace přes Postman
V Postmanu vytvořte request:
- Method: `POST`
- URL: `http://api.localhost/integration/verifications`
- Headers:
```text
Content-Type: application/json
X-API-Key: pk_...
X-API-Secret: sk_...
```
- Body:
```json
{
  "journeyTemplateId": "11111111-2222-3333-4444-555555555555",
  "expiresAt": "2026-12-31T23:59:59Z",
  "externalReference": "customer-123"
}
```

Poznámky:
- `journeyTemplateId` musí patřit tenantovi API klíče a template musí být `ACTIVE`.
- `expiresAt` je volitelné.
- Odpověď `201` obsahuje `verificationUrl`.

### 6.5 Průchod ve verify aplikaci
1. Otevřete `verificationUrl` z předchozího kroku (např. `http://verify.localhost/v/{token}`).
2. Dokončete všechny povinné kroky:
- osobní údaje,
- doklad totožnosti,
- liveness.
3. Pokud jste zapnuli volitelné OTP kroky:
- v test/dev režimu OTP kódy najdete v API logu (`[DEMO] OTP ...`) nebo v response volání `send-code`.
4. Po dokončení flow se provede finalizace a verifikace přejde do auto-check pipeline.

### 6.6 Sledování průběhu v admin
1. Otevřete:
```text
http://admin.localhost/t/{tenantSlug}/verifications
```
2. Otevřete detail konkrétní verifikace.
3. Sledujte:
- stav verifikace (`INITIATED` -> `IN_PROGRESS` -> `READY_FOR_AUTOCHECK` -> finální stav),
- check results (doklad, liveness, face match, AML),
- případně audit logy a webhook delivery (pokud je máte zapnuté).

Volitelně lze současně sledovat runtime logy:
```bash
docker compose logs -f api worker
```

## 7. Stop a úklid

```bash
docker compose down
```

Smazání volume dat (DB/RabbitMQ/MinIO):

```bash
docker compose down -v
```
