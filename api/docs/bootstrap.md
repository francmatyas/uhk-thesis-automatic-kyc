# Bootstrap sekvence

## 1. Účel dokumentu
Tento dokument popisuje inicializační (bootstrap) mechanismy spuštěné při startu aplikace. Cílem je zajistit:
- katalog oprávnění a rolí,
- výchozího administrátora,
- testovací tenant a výchozí KYC data pro lokální/test prostředí.

## 2. Přehled bootstrap rutin
V aktuální verzi jsou v aplikaci tyto `ApplicationRunner` rutiny:

1. `BootstrapAdminRunner` (`@Order(1)`, podmínka `app.bootstrap.enabled=true`)
2. `BootstrapAuthorizationCatalogRunner` (`@Order(2)`, podmínka `app.bootstrap.authz.enabled=true`, default zapnuto)
3. `BootstrapTestTenantRunner` (`@Order(3)`, podmínka `app.bootstrap.enabled=true`)
4. `BootstrapJourneyTemplateRunner` (`@Order(4)`, podmínka `app.bootstrap.enabled=true`)
5. `BootstrapTestVerificationRunner` (`@Order(10)`, podmínka `app.bootstrap.test-verification.enabled=true`)

## 3. Seedování katalogu oprávnění a rolí

### 3.1 Aktivace

```yaml
app:
  bootstrap:
    authz:
      enabled: true
```

### 3.2 Funkce rutiny
`BootstrapAuthorizationCatalogRunner` zajišťuje:
- vytvoření chybějících oprávnění (unikátně podle `(resource, action)`),
- vytvoření chybějících rolí (unikátně podle `(name, scope)`),
- vytvoření chybějících vazeb role ↔ oprávnění.

### 3.3 Seedovaný katalog (hlavní skupiny)
Oprávnění:
- provider doména: `provider.tenants`, `provider.users`, `provider.roles`, `provider.permissions`, `provider.audit-logs`,
- provider KYC doména: `provider.verifications`, `provider.journey-templates`,
- tenant doména: `tenant.tenants`, `tenant.members`, `tenant.roles`, `tenant.api-keys`, `tenant.webhooks`, `tenant.audit-logs`,
- tenant KYC doména: `tenant.journey-templates`, `tenant.client-identities`, `tenant.verifications`.

Role:
| Role | Scope | Priorita |
|---|---|---|
| OWNER | PROVIDER | 1200 |
| ADMIN | PROVIDER | 1000 |
| SUPPORT | PROVIDER | 500 |
| OWNER | TENANT | 1000 |
| ADMIN | TENANT | 900 |
| OPERATOR | TENANT | 500 |
| VIEWER | TENANT | 100 |

### 3.4 Idempotence
Opakované spuštění:
- nevytváří duplicity,
- pouze doplňuje chybějící záznamy.

## 4. Seedování administrátorského uživatele

### 4.1 Aktivace a konfigurace

```yaml
app:
  bootstrap:
    enabled: true
    admin-email: admin@example.com
    admin-password: TajneHeslo123!
    admin-given-name: Root
    admin-family-name: User
    admin-role-name: OWNER
    admin-role-scope: PROVIDER
```

Alternativně přes proměnné prostředí:

```text
APP_BOOTSTRAP_ENABLED=true
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
APP_BOOTSTRAP_ADMIN_PASSWORD=TajneHeslo123!
```

### 4.2 Funkce rutiny
`BootstrapAdminRunner`:
1. ověří přítomnost `admin-email` a `admin-password`,
2. načte nebo vytvoří roli podle `(admin-role-name, admin-role-scope)`,
3. vyhledá uživatele přes `UserEmailLookupService`,
4. pokud uživatel existuje:
   - nastaví `isProviderUser=true` (pokud není),
   - zajistí přiřazení provider role v `user_tenant_roles` (`tenant=null`),
5. pokud uživatel neexistuje:
   - vytvoří účet,
   - vytvoří `UserProfile` a `UserPreferences`,
   - vytvoří provider přiřazení role.

Poznámka:
- pro bootstrap administrátora se heslo ukládá jako `BCrypt(SHA-256(plainPassword))`.

### 4.3 Omezení
- Podporovaný scope bootstrap role je `PROVIDER`.
- Pokud je `admin-role-scope=TENANT`, rutina se přeskočí s varováním.

## 5. Test tenant a výchozí journey template

### 5.1 Test tenant (`BootstrapTestTenantRunner`)
Při `app.bootstrap.enabled=true`:
- dohledá admin uživatele podle `app.bootstrap.admin-email`,
- vytvoří (pokud chybí) tenant podle:
  - `app.bootstrap.test-tenant.slug` (default `test-tenant`),
  - `app.bootstrap.test-tenant.name` (default `Test Tenant`),
- zajistí membership a `TENANT OWNER` roli pro admina.

### 5.2 Journey template (`BootstrapJourneyTemplateRunner`)
Při `app.bootstrap.enabled=true`:
- v test tenantu vytvoří (pokud chybí) šablonu `Full KYC`,
- šablona je aktivní a obsahuje základní config (např. `allowedDocumentTypes`).

## 6. Test verifikace (`BootstrapTestVerificationRunner`)
Při `app.bootstrap.test-verification.enabled=true`:
- nahraje testovací obrázky do storage (`bootstrap/*`),
- vytvoří testovací `ClientIdentity`,
- vytvoří `Verification` a připraví ji do `READY_FOR_AUTOCHECK`,
- dispatchne AML, kontrolu dokladu, liveness a face-match.

Hlavní konfigurační klíče:
- `app.bootstrap.test-verification.enabled`
- `app.bootstrap.test-verification.image-key-prefix`
- `app.bootstrap.test-verification.first-name`
- `app.bootstrap.test-verification.last-name`
- `app.bootstrap.test-verification.date-of-birth`
- `app.bootstrap.test-verification.country-of-residence`

## 7. Pořadí spuštění při startu aplikace

```text
Flyway migrace (pokud jsou zapnuté)
    ↓
BootstrapAdminRunner
    ↓
BootstrapAuthorizationCatalogRunner
    ↓
BootstrapTestTenantRunner
    ↓
BootstrapJourneyTemplateRunner
    ↓
BootstrapTestVerificationRunner (jen pokud enabled)
    ↓
Aplikace připravena přijímat požadavky
```

Poznámka:
- v aktuálním `application.yml` je `spring.flyway.enabled=false` (typicky pro lokální vývoj).

## 8. Provozní doporučení
- Po prvním nasazení změnit heslo bootstrap administrátora.
- V produkci vypnout bootstrap uživatele (`APP_BOOTSTRAP_ENABLED=false`) po inicializaci.
- `APP_BOOTSTRAP_ADMIN_PASSWORD` ukládat mimo repozitář (secret manager).
- `authz.enabled` lze ponechat aktivní; idempotentní seedování pomáhá při evoluci oprávnění.

## 9. Související dokumentace
- [RBAC](./rbac.md)
- [Autentizace](./authentication.md)
