# Webhooky

## 1. Účel dokumentu
Dokument popisuje správu webhook endpointů a provoz doručování webhook událostí v systému.

## 2. Základní koncept
Webhook endpoint je tenant-scoped objekt s atributy:
- `url` (cílová adresa),
- `secret` (podpisový klíč),
- `status` (`ACTIVE` nebo `DISABLED`),
- odebírané `eventTypes`.

`url` i `secret` jsou perzistentně ukládány šifrovaně.

## 3. Správa webhook endpointů
Správa probíhá pod `/integrations` a vyžaduje autentizovanou uživatelskou session s aktivním tenant kontextem.

### 3.1 Přehled endpointů
| Operace | Metoda a cesta | Poznámka |
|---|---|---|
| Seznam | `GET /integrations/webhooks` | Stránkovaný tabulkový výstup |
| Form options | `GET /integrations/webhooks/options` | Statusy a event typy |
| Detail | `GET /integrations/webhooks/{id}` | Metadata endpointu |
| Vytvoření | `POST /integrations/webhooks` | Vytvoří endpoint a subscriptions |
| Aktualizace | `PUT /integrations/webhooks/{id}` | Mění `status` a `eventTypes` |
| Smazání | `DELETE /integrations/webhooks/{id}` | Soft-delete endpointu |

### 3.2 Seznam
- Query parametry: `page`, `size`, `sort`, `dir`, `q`.
- Odpověď obsahuje:
  - `columns`, `rows`,
  - `pageNumber`, `pageSize`,
  - `totalPages`, `totalElements`.

### 3.3 Options endpoint
`GET /integrations/webhooks/options` vrací:
- `statuses`: `ACTIVE`, `DISABLED`,
- `eventTypes`: aktuální katalog eventů (viz sekce 4).

### 3.4 Detail endpointu
Vrací:
- `id`, `tenantId`, `url`, `status`, `createdAt`, `lastDeliveryAt`,
- `eventTypes`,
- `eventTypeOptions`.

`secret` se v detailu nevrací.

### 3.5 Vytvoření endpointu
Příklad:

```json
{
  "url": "https://example.com/webhooks/automatic-kyc",
  "secret": "whsec_vlastni_tajny_retezec",
  "eventTypes": ["verification.completed", "verification.failed"]
}
```

Pravidla:
- `url` je povinné (`http`/`https`, validní host),
- URL nesmí resolveovat na privátní/loopback/link-local adresy (SSRF ochrana),
- `secret` je volitelné:
  - pokud chybí, je vygenerováno,
  - pokud je posláno, validuje se rozsah délky `16..255`,
- `eventTypes` je volitelné:
  - při create a prázdné hodnotě se endpoint subscribuje na všechny dostupné eventy,
  - duplicity se deduplikují.

### 3.6 Aktualizace endpointu
Příklad:

```json
{
  "status": "DISABLED",
  "eventTypes": ["verification.completed"]
}
```

Pravidla:
- povolená pole jsou pouze `status` a `eventTypes`,
- `status` musí být `ACTIVE` nebo `DISABLED`,
- `url` ani `secret` se přes update endpoint nemění,
- `eventTypes`:
  - `null` znamená bez změny,
  - `[]` znamená odhlášení ze všech eventů,
- payload bez efektivní změny vrací `update_required`.

### 3.7 Smazání endpointu
- `DELETE /integrations/webhooks/{id}` vrací `204`,
- endpoint je soft-deleted (`is_deleted=true`),
- subscriptions se při mazání endpointu odstraňují.

## 4. Podporované události
Aktuální katalog:
- `document.ready`
- `document.deleted`
- `verification.completed`
- `verification.failed`
- `verification.requires_review`
- `verification.approved`
- `verification.rejected`
- `verification.expired`

Emitování událostí:
- `document.*`: `DocumentService`
- `verification.completed|failed|requires_review`: `VerificationStepService`
- `verification.approved|rejected|expired`: `VerificationService`

## 5. Kontrakt odchozího webhook požadavku
Dispatcher odesílá na endpoint HTTP `POST` s JSON payloadem.

### 5.1 Hlavičky
- `Content-Type: application/json`
- `User-Agent: automatic-kyc-webhook-dispatcher/1.0`
- `X-AutomaticKyc-Event: <eventType>`
- `X-AutomaticKyc-Delivery-Id: <deliveryJobId>`
- `X-AutomaticKyc-Attempt: <attemptNo>`
- `X-AutomaticKyc-Timestamp: <unix-seconds>`
- `X-AutomaticKyc-Signature: v1=<hex_hmac_sha256>`
- volitelně: `X-Correlation-Id`, `X-Request-Id`

### 5.2 JSON envelope

```json
{
  "id": "delivery-job-uuid",
  "type": "verification.completed",
  "createdAt": "2026-03-14T10:15:30Z",
  "attempt": 1,
  "tenantId": "tenant-uuid",
  "data": {
    "verificationId": "verification-uuid",
    "tenantId": "tenant-uuid",
    "status": "AUTO_PASSED",
    "overallScore": 0.91,
    "journeyTemplateId": "journey-template-uuid",
    "completedAt": "2026-03-14T10:15:00Z",
    "createdAt": "2026-03-14T09:59:00Z"
  }
}
```

Pole `correlationId` a `requestId` se mohou objevit, pokud jsou přítomná při enqueue.
Struktura `data` je závislá na typu eventu.

## 6. Ověření podpisu
Podpis je založen na:
- `signed_payload = X-AutomaticKyc-Timestamp + "." + raw_request_body`,
- `expected = "v1=" + hex(HMAC_SHA256(secret, signed_payload))`.

Příklad (Node.js):

```js
import crypto from "node:crypto";

function verifySignature(secret, timestamp, rawBody, receivedSignature) {
  const payload = `${timestamp}.${rawBody}`;
  const digest = crypto.createHmac("sha256", secret).update(payload, "utf8").digest("hex");
  const expected = `v1=${digest}`;
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(receivedSignature));
}
```

## 7. Doručování a retry politika
- doručování je asynchronní (tabulka `webhook_delivery_jobs` + scheduler),
- úspěch je HTTP `2xx`,
- retryable HTTP stavy:
  - `408`, `425`, `429`, `500`, `502`, `503`, `504`,
- retry také při síťové I/O chybě,
- používá se exponenciální backoff s jitterem,
- po vyčerpání pokusů je job označen jako `FAILED`,
- při úspěchu se endpointu aktualizuje `lastDeliveryAt`.

## 8. Konfigurace dispatcheru
Konfigurační sekce: `app.webhooks.dispatcher`.

| Parametr | Výchozí hodnota |
|---|---|
| `enabled` | `true` |
| `poll-ms` | `3000` |
| `batch-size` | `25` |
| `max-attempts` | `6` |
| `base-delay-ms` | `1000` |
| `max-delay-ms` | `60000` |
| `connect-timeout-ms` | `3000` |
| `request-timeout-ms` | `10000` |

## 9. Chybové stavy (správa endpointů)
| HTTP stav a chyba | Význam |
|---|---|
| `400 {"error":"invalid_webhook_id"}` | Neplatné UUID v path parametru |
| `404 {"error":"webhook_not_found"}` | Endpoint neexistuje v tenant scope |
| `400 {"error":"url_required"}` | Chybí `url` při create |
| `400 {"error":"invalid_webhook_url"}` | URL není validní `http`/`https` adresa nebo míří na privátní adresu |
| `400 {"error":"invalid_webhook_status"}` | Neplatný `status` |
| `400 {"error":"invalid_webhook_event_type"}` | Neplatná hodnota v `eventTypes` |
| `400 {"error":"update_required"}` | Aktualizační payload bez efektivní změny |
| `400 {"error":"tenant_required"}` | Chybí aktivní tenant kontext |
| `403 {"error":"forbidden","reason":"not_member_of_tenant"}` | Uživatel nemá přístup do tenanta |

## 10. Související dokumentace
- [Šifrování a ochrana PII](./encryption-pii.md)
- [Autentizace](./authentication.md)
