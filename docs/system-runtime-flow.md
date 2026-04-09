# Jak systém funguje

## Přehled komponent
- `verify` (React): sběr dat od koncového uživatele.
- `api` (Spring Boot): orchestrace kroků, persistence, autorizace, audit, webhooky.
- `worker` (Python): asynchronní KYC kontroly (OCR/doklady, biometrie, AML).
- `rabbitmq`: messaging mezi `api` a `worker`.
- `postgres`: perzistence doménových dat.
- `minio` (S3): uložení dokumentů a selfie/liveness snímků.

## End-to-end průběh verifikace
1. Uživatel otevře odkaz `verify` aplikace (`/v/:token`).
2. `verify` načte flow z API (`GET /flow/verify/v1/{token}`) a sestaví pořadí kroků podle `journeyConfig`.
3. Uživatel postupně vyplní kroky (osobní údaje, OTP, doklad, liveness, AML).
4. Pro dokumenty a liveness:
- `verify` požádá API o presigned upload URL,
- binární data se nahrají přímo do MinIO,
- `verify` zavolá `complete` endpoint a následně krokové endpointy (`/id-document`, `/liveness`).
5. Po dokončení povinných kroků `verify` zavolá `POST /flow/verify/v1/{token}/finalize`.
6. `api` přepne stav verifikace a dispatchne asynchronní KYC úlohy do RabbitMQ.
7. `worker` zpracuje úlohy, publikuje průběh/výsledky, API je uloží do DB a zpřístupní v `admin`.

## Řízení kroků flow
- Kanonické pořadí kroků je definované ve `verify/src/api/verification.js` (`STEP_ORDER`).
- Povinné kroky:
- `PERSONAL_INFO`
- `DOCUMENT_IDENTITY`
- `LIVENESS_CHECK`
- Volitelné kroky se aktivují přes `journeyConfig.optionalSteps`.
- `verify` mapuje backend statusy na UI průchod a průběžný progress.

## Chování při chybách a expiraci
- `verify` rozlišuje minimálně tyto stavy:
- `verification_already_submitted`
- `verification_expired`
- `not_found`
- API vrací odpovídající HTTP statusy (`404`, `410`, `409`, `422`, `403`) podle typu problému.

## Bezpečnostní body
- Mutační požadavky ve `verify` používají CSRF token z `GET /auth/csrf`.
- Přenos mezi `api` a `rabbitmq` běží přes TLS/mTLS.
- PII data v API vrstvě jsou šifrovaná podle konfigurace `APP_ENCRYPTION_MASTER_KEY`.

