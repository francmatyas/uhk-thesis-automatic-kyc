# Audit logy

## 1. Účel dokumentu
Tento dokument popisuje aktuální implementaci audit logů v Automatic KYC API, konkrétně:
- datový model `audit_logs`,
- způsob zápisu přes `AuditLogService`,
- dostupné read endpointy pro tenant/provider kontext,
- současné zdroje auditních událostí.

## 2. Architektura a rozsah
Audit vrstva je implementována jako centrální append-only tabulka `audit_logs`.

Aktuálně je k dispozici:
- zápis auditních záznamů z více controllerů,
- čtení auditních záznamů přes:
  - `GET /tenants/audit-logs` a `GET /tenants/audit-logs/{id}`,
  - `GET /provider/audit-logs` a `GET /provider/audit-logs/{id}`.

## 3. Datový model (`audit_logs`)

### 3.1 Klíčové sloupce
| Sloupec | Typ | Význam |
|---|---|---|
| `id` | `uuid` | Primární klíč |
| `created_at` | `timestamp with time zone` | Čas vytvoření |
| `tenant_id` | `uuid` | Tenant kontext (volitelné) |
| `actor_type` | `varchar(16)` | `USER`, `API_KEY`, `SYSTEM`, `SERVICE` |
| `actor_user_id` | `uuid` | Aktér uživatel |
| `actor_api_key_id` | `uuid` | Aktér API klíč |
| `entity_type` | `varchar(64)` | Typ entity |
| `entity_id` | `text` | Identifikátor entity |
| `action` | `varchar(64)` | Název akce |
| `old_value` | `jsonb` | Původní stav |
| `new_value` | `jsonb` | Nový stav |
| `metadata` | `jsonb` | Doplňková metadata |
| `ip_address` | `inet` | Zdrojová IP adresa |
| `user_agent` | `text` | User-Agent |
| `correlation_id` | `uuid` | Korelační identifikátor |
| `request_id` | `varchar(128)` | Identifikátor requestu |
| `result` | `varchar(16)` | `SUCCESS`, `FAILURE` |
| `error_code` | `varchar(64)` | Aplikační kód chyby |

### 3.3 Integritní omezení
Databáze vynucuje:
- povolené hodnoty `actor_type`,
- povolené hodnoty `result`,
- konzistenci aktéra:
  - `USER` vyžaduje `actor_user_id` a zakazuje `actor_api_key_id`,
  - `API_KEY` vyžaduje `actor_api_key_id` a zakazuje `actor_user_id`,
  - `SYSTEM`/`SERVICE` zakazují oba odkazy.

Cizí klíče:
- `tenant_id -> tenants(id)` (`on delete set null`),
- `actor_user_id -> users(id)` (`on delete set null`),
- `actor_api_key_id -> api_keys(id)` (`on delete set null`).

## 4. Neměnnost (append-only)
Neměnnost je zajištěna ve dvou vrstvách:
- aplikačně:
  - entita `AuditLog` je označena jako `@Immutable`,
- databázově:
  - trigger `trg_audit_logs_no_update` blokuje `UPDATE`,
  - trigger `trg_audit_logs_no_delete` blokuje `DELETE`.

## 5. Zápis audit logů (`AuditLogService`)

### 5.1 Vstupní API
Zápis je centralizován ve službě `AuditLogService`:
- `log(AuditLogCommand command)`,
- `logUserAction(...)` (helper pro uživatelské akce).

### 5.2 Normalizace a validace
Služba:
- odvozuje `actorType`, pokud není explicitně uveden,
- validuje konzistenci reference aktéra (`actorUserId` vs `actorApiKeyId`),
- vyžaduje neprázdné:
  - `entityType` (max 64),
  - `entityId`,
  - `action` (max 64),
- převádí `oldValue` a `newValue` na `JsonNode`,
- normalizuje `metadata` (default prázdná mapa),
- normalizuje IP adresu (včetně formátů `X-Forwarded-For`, `for=`, IPv6 bracket),
- ořezává:
  - `requestId` na 128 znaků,
  - `errorCode` na 64 znaků.

### 5.3 Chování při selhání auditu
Většina producentů volá audit v `try/catch` a případné chyby při zápisu ignoruje, tj. audit je implementován jako best-effort a standardně neblokuje hlavní business operaci.

## 6. Aktuální producenti auditních událostí
Audit zápisy jsou aktuálně generovány v následujících controllerech:

| Modul | Akce (příklady) | Poznámka |
|---|---|---|
| `AuthenticationController` | `LOGIN`, `LOGOUT`, `REGISTER` | Při neúspěšném loginu se zapisuje `FAILURE` se `SYSTEM` aktérem |
| `SessionController` | `SESSION_REVOKE`, `SESSION_REVOKE_ALL` | Session management |
| `TenantProviderController` | `TENANT_CREATE`, `TENANT_UPDATE`, `TENANT_DELETE` | Provider správa tenantů |
| `TenantMeController` | `TENANT_UPDATE` | Tenant self-update |
| `TenantSwitchController` | `SWITCH_ACTIVE_TENANT` | Obsahuje `oldValue/newValue` a metadata switch operace |
| `RoleController` | `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_DELETE` | Provider role management |
| `PermissionController` | `PERMISSION_CREATE`, `PERMISSION_UPDATE`, `PERMISSION_DELETE` | Provider permission management |

## 7. Čtení audit logů (API)

### 7.1 Tenant API
- `GET /tenants/audit-logs`
- `GET /tenants/audit-logs/{id}`

Autorizace:
- `@RequireActiveTenant`,
- `PERM_tenant.audit-logs:read`.

Filtry list endpointu:
- `page` (default `0`),
- `size` (default `25`, interně limit `1..100`),
- `entityType`,
- `action`,
- `actorUserId` (UUID).

### 7.2 Provider API
- `GET /provider/audit-logs`
- `GET /provider/audit-logs/{id}`

Autorizace:
- `PERM_provider.audit-logs:read`.

Filtry list endpointu:
- `page` (default `0`),
- `size` (default `25`, interně limit `1..100`),
- `tenantId` (UUID, volitelně),
- `entityType`,
- `action`,
- `actorUserId` (UUID).

### 7.3 Formát list odpovědi
Oba list endpointy vracejí `TableDTO`:
- `columns`,
- `rows`,
- `pageNumber`,
- `pageSize`,
- `totalPages`,
- `totalElements`.

List DTO zobrazuje:
- `action` (Event),
- `createdAt` (Time),
- `actorType` (Actor),
- `entityType` (Entity),
- `result` (Result).

### 7.4 Formát detail odpovědi
Detail endpoint vrací `AuditLogDetailDTO` s poli:
- `id`,
- `createdAt`,
- `tenantId`,
- `actorType`,
- `entityType`,
- `action`,
- `result`,
- `errorCode`.

## 8. Oprávnění a seed katalog
Audit read oprávnění jsou součástí bootstrap katalogu:
- `provider.audit-logs:read`,
- `tenant.audit-logs:read`.

V default role-grantech:
- provider `OWNER` a `ADMIN` mají `provider.audit-logs:read`,
- tenant `OWNER` má `tenant.audit-logs:read`.

## 9. Chybové stavy API
| Endpoint skupina | Chyba | Význam |
|---|---|---|
| tenant/provider list | `401` | Chybějící autentizace |
| tenant list/detail | `400 {"error":"tenant_required"}` | Chybí aktivní tenant kontext |
| provider list | `400 {"error":"invalid_tenant_id"}` | Neplatné `tenantId` |
| tenant/provider list | `400 {"error":"invalid_actor_user_id"}` | Neplatné `actorUserId` |
| tenant/provider detail | `400 {"error":"invalid_audit_log_id"}` | Neplatné UUID detailu |
| tenant/provider detail | `404 {"error":"audit_log_not_found"}` | Audit záznam neexistuje nebo není dostupný v tenant scope |

## 10. Historie migrací
- `V2__session_switch_audit.sql`: původní specializovaná tabulka `session_switch_audits`.
- `V6__audit_logs.sql`: zavedení obecné tabulky `audit_logs`.
- `V7__drop_session_switch_audits.sql`: odstranění původní tabulky.

## 11. Související dokumentace
- [Autentizace](./authentication.md)
- [RBAC](./rbac.md)
- [Multi-tenancy](./multi-tenancy.md)
