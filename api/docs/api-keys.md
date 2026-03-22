# API klíče

## 1. Účel dokumentu
Tento dokument popisuje životní cyklus API klíčů v systému: jejich vytvoření, správu, použití při autentizaci požadavků a související bezpečnostní pravidla.

## 2. Datový model a základní pojmy
API klíč je vždy navázán na konkrétního tenanta.

### 2.1 Struktura klíče
- `publicKey`:
  - přenášen v hlavičce `X-API-Key`,
  - formát začíná prefixem `pk_`.
- `secret`:
  - přenášen v hlavičce `X-API-Secret`,
  - formát začíná prefixem `sk_`,
  - vrácen pouze při vytvoření.

### 2.2 Perzistence
- `publicKey` je uložen v čitelné podobě.
- `secret` je ukládán pouze jako hash (`secretHash`); původní hodnota není po vytvoření obnovitelná.
- Podporované stavy klíče:
  - `ACTIVE`
  - `REVOKED`

## 3. Správa API klíčů (uživatelská session)
Správa klíčů probíhá pod prefixem `/integrations` a vyžaduje autentizovanou uživatelskou session.

### 3.1 Přehled endpointů
| Operace | Metoda a cesta | Poznámka |
|---|---|---|
| Seznam klíčů | `GET /integrations/api-keys` | Stránkovaný tabulkový výstup |
| Detail klíče | `GET /integrations/api-keys/{id}` | Vrací metadata, nevrací `secret` |
| Vytvoření klíče | `POST /integrations/api-keys` | Vrací `secret` pouze jednou |
| Aktualizace klíče | `PUT /integrations/api-keys/{id}` | Podporuje změnu `name` a `status` |
| Smazání klíče | `DELETE /integrations/api-keys/{id}` | Soft-delete (`is_deleted=true`) |

### 3.2 Seznam klíčů
- Query parametry: `page`, `size`, `sort`, `dir`, `q`.
- Výstup obsahuje pole:
  - `columns`
  - `rows`
  - `pageNumber`
  - `pageSize`
  - `totalPages`
  - `totalElements`

### 3.3 Detail klíče
Endpoint vrací metadata:
- `id`
- `tenantId`
- `name`
- `publicKey`
- `status`
- `createdAt`
- `lastUsedAt`

`secret` se v detailu nikdy nevrací.

### 3.4 Vytvoření klíče
Příklad payloadu:

```json
{
  "name": "CI klíč"
}
```

Pravidla:
- `name` je povinné.
- maximální délka je `255` znaků.
- odpověď vrací HTTP `201` a jednorázově i vygenerovaný `secret`.

### 3.5 Aktualizace klíče
Příklad payloadu:

```json
{
  "name": "Přejmenovaný klíč",
  "status": "REVOKED"
}
```

Pravidla:
- payload musí obsahovat alespoň jednu efektivní změnu,
- `status` musí být `ACTIVE` nebo `REVOKED`.

### 3.6 Smazání klíče
- Endpoint vrací HTTP `204`.
- Mazání je realizováno jako soft-delete.

## 4. Autentizace požadavku pomocí API klíče
Každý požadavek musí obsahovat obě hlavičky:
- `X-API-Key: pk_...`
- `X-API-Secret: sk_...`

Volitelně lze poslat:
- `X-Tenant-Id: <tenant-uuid>`

Pravidla:
- pokud je `X-Tenant-Id` uvedeno a neodpovídá tenantovi klíče, požadavek je odmítnut,
- pokud chybí jedna z dvojice hlaviček klíče, požadavek je odmítnut,
- `lastUsedAt` se aktualizuje při úspěšné autentizaci.

Příklad volání:

```bash
curl -X GET 'http://localhost:8080/market-tickers?symbol=BTCUSD&timeframe=5m' \
  -H 'X-API-Key: pk_...' \
  -H 'X-API-Secret: sk_...'
```

## 5. Omezení přístupu při API-key autentizaci
- API-key autentizace je blokována pro cesty `/auth/**` a `/users/**`.
- API klíč může volat pouze endpointy označené anotací `@ApiKeyAccessible`.
- Aktuálně je takto dostupný endpoint:
  - `GET /market-tickers`

## 6. CSRF chování
Požadavky autentizované API klíčem mohou obejít CSRF kontrolu, pokud jsou splněny obě podmínky:
- přítomné jsou obě API-key hlavičky,
- požadavek neobsahuje JWT cookie.

## 7. Chybové stavy
| HTTP stav a chyba | Význam |
|---|---|
| `401 {"error":"unauthorized","reason":"invalid_api_key_credentials"}` | Chybějící/nesprávné hlavičky, neplatný secret, neexistující nebo neaktivní klíč |
| `403 {"error":"forbidden","reason":"api_key_forbidden_path"}` | Pokus o použití API klíče na `/auth/**` nebo `/users/**` |
| `403 {"error":"forbidden","reason":"api_key_endpoint_not_enabled"}` | Endpoint není označen jako `@ApiKeyAccessible` |
| `403 {"error":"forbidden","reason":"tenant_mismatch_for_api_key"}` | `X-Tenant-Id` neodpovídá tenantovi klíče |
| `400 {"error":"invalid_api_key_id"}` | Neplatné UUID v path parametru |
| `404 {"error":"api_key_not_found"}` | Klíč nebyl nalezen v aktuálním tenant kontextu |
| `400 {"error":"invalid_api_key_status"}` | Neplatný stav při aktualizaci |
| `400 {"error":"name_required"}` | Chybějící nebo prázdné `name` |
| `400 {"error":"update_required"}` | Aktualizační payload neobsahuje efektivní změnu |

## 8. Související dokumentace
- [Autentizace](./authentication.md)
- [Multi-tenancy](./multi-tenancy.md)
- [Šifrování a ochrana PII](./encryption-pii.md)
