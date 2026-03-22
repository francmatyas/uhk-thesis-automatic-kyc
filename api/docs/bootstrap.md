# Bootstrap sekvence

## 1. Účel dokumentu
Tento dokument popisuje inicializační (bootstrap) mechanismy spuštěné při startu aplikace. Cílem je zajistit:
- existenci katalogu oprávnění a rolí,
- existenci výchozího administrátorského účtu.

## 2. Přehled bootstrap rutin
Při startu aplikace jsou spuštěny dvě hlavní rutiny:
1. `BootstrapAuthorizationCatalogRunner`
2. `BootstrapAdminRunner`

Obě rutiny jsou navrženy jako idempotentní.

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

### 3.3 Seedovaný katalog
#### Oprávnění
- `provider.tenants`, `provider.users`, `provider.roles`, `provider.permissions`:
  - akce `read`, `create`, `update`, `delete`.
- `tenant.tenants`, `tenant.members`, `tenant.roles`:
  - akce dle relevance (typicky `read`, `create`, `update`, `delete`).
- `tenant.api-keys`, `tenant.webhooks`:
  - akce `read`, `create`, `update`, `delete`.

#### Role
| Role | Scope | Priorita |
|---|---|---|
| OWNER | PROVIDER | 1200 |
| ADMIN | PROVIDER | 1000 |
| OWNER | TENANT | 1000 |
| ADMIN | TENANT | 900 |
| OPERATOR | TENANT | 500 |

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
   - zajistí přiřazení provider role,
5. pokud uživatel neexistuje:
   - vytvoří účet s BCrypt heslem,
   - vytvoří `UserProfile` a `UserPreferences`,
   - vytvoří provider přiřazení role (`UserTenantRole` s `tenant=null`).

### 4.3 Omezení
- Podporovaný scope bootstrap role je `PROVIDER`.
- Pokud je `admin-role-scope=TENANT`, konfigurace je ignorována (varování v logu).
- Při `enabled=false` se rutina nespouští.

### 4.4 Idempotence
Rutina je bezpečná pro opakované spuštění:
- neduplikuje uživatele,
- nepřepisuje existující heslo ani profilová data bez explicitní logiky.

## 5. Pořadí spuštění při startu aplikace

```text
Flyway migrace
    ↓
BootstrapAuthorizationCatalogRunner
    ↓
BootstrapAdminRunner
    ↓
Aplikace připravena přijímat požadavky
```

Flyway migrace probíhají před `ApplicationRunner` beany, takže bootstrap operuje nad aktuálním schématem.

## 6. Provozní doporučení
- Po prvním nasazení změnit heslo bootstrap administrátora.
- V produkci vypnout bootstrap uživatele (`APP_BOOTSTRAP_ENABLED=false`) po inicializaci.
- `APP_BOOTSTRAP_ADMIN_PASSWORD` ukládat mimo repozitář (např. secret manager).
- `authz.enabled` lze ponechat aktivní; idempotentní seedování pomáhá při evoluci oprávnění.

## 7. Související dokumentace
- [RBAC](./rbac.md)
- [Autentizace](./authentication.md)
