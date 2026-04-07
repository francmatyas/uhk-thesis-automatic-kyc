# RBAC (Role-Based Access Control)

## 1. Účel dokumentu
Dokument popisuje model autorizace založený na RBAC, tedy na přiřazení:
- oprávnění k rolím,
- rolí k uživatelům v konkrétním kontextu (provider nebo tenant).

## 2. Konceptuální model (aktuální runtime)

```text
Permission
    ↑ (N:M)
RolePermission
    ↓
Role (scope: PROVIDER | TENANT)
    ↑ (N:M)
UserTenantRole (user + role + tenant?)
    ↓
User
```

Poznámka:
- runtime výpočet oprávnění používá `user_tenant_roles`,
- tabulka `user_roles` je v modelu stále přítomná, ale není zdrojem policy snapshotu.

## 3. Oprávnění (`Permission`)
Oprávnění je definováno dvojicí `(resource, action)`.

| Pole | Popis |
|---|---|
| `resource` | Logická doména, např. `tenant.members` |
| `action` | Operace, např. `read`, `create`, `update`, `delete` |
| `label` | Synchronizované pole `resource:action` |

Kombinace `(resource, action)` je unikátní.

### 3.1 Katalog zdrojů
| Zdroj | Podporované akce |
|---|---|
| `provider.tenants` | read, create, update, delete |
| `provider.users` | read, create, update, delete |
| `provider.roles` | read, create, update, delete |
| `provider.permissions` | read |
| `provider.audit-logs` | read |
| `provider.verifications` | read |
| `provider.journey-templates` | read, create, update, delete |
| `tenant.tenants` | read, update |
| `tenant.members` | read, create, update, delete |
| `tenant.roles` | read, update |
| `tenant.api-keys` | read, create, update, delete |
| `tenant.webhooks` | read, create, update, delete |
| `tenant.audit-logs` | read |
| `tenant.journey-templates` | read, create, update, delete |
| `tenant.client-identities` | read |
| `tenant.verifications` | read, review |

Poznámka:
- manuální review verifikací (`approve/reject`) je aktuálně svázané s tenant oprávněním `tenant.verifications:review`.

## 4. Role (`Role`)
Role jsou definovány jménem a rozsahem (`scope`).

| Pole | Význam |
|---|---|
| `name` | Název role (např. `ADMIN`) |
| `scope` | `PROVIDER` nebo `TENANT` |
| `slug` | Strojově stabilní identifikátor |
| `priority` | Priorita pro prezentační vrstvy |

### 4.1 Předdefinované role
| Role | Scope | Priorita | Typické pokrytí |
|---|---|---|---|
| OWNER | PROVIDER | 1200 | `provider.*` |
| ADMIN | PROVIDER | 1000 | `provider.*` |
| SUPPORT | PROVIDER | 500 | Vybraná read oprávnění |
| OWNER | TENANT | 1000 | `tenant.*` |
| ADMIN | TENANT | 900 | Správa tenanta, členů a KYC domén |
| OPERATOR | TENANT | 500 | Práce s KYC (bez plné správy tenanta) |
| VIEWER | TENANT | 100 | Read-only KYC přístup |

## 5. Přiřazení rolí uživatelům (`UserTenantRole`)
Tabulka `user_tenant_roles` určuje, v jakém kontextu role platí.

| Hodnota `tenant_id` | Význam |
|---|---|
| `NULL` | Provider kontext |
| UUID tenanta | Tenant kontext |

Konzistence je validována při ukládání:
- provider role (`scope=PROVIDER`) vyžaduje `tenant_id = NULL`,
- tenant role (`scope=TENANT`) vyžaduje `tenant_id != NULL`.

## 6. Sestavení oprávnění (`PolicyService`)
Při každém požadavku `PolicyService.buildForUser(userId)` sestaví `PolicySnapshot` z aktuálního stavu DB.

Výsledný snapshot obsahuje:
- `roles`,
- `permissions` ve formátu `resource:action`,
- `policyVersion`.

Chování podle kontextu:
- provider bez aktivního tenanta:
  - provider role (`tenant_id = NULL`).
- provider s aktivním tenantem:
  - tenant role pro aktivního tenanta (provider scope se v tomto režimu nepromítá).
- tenant uživatel s aktivním tenantem:
  - tenant role pro aktivního tenanta.
- tenant uživatel bez aktivního tenanta:
  - prázdný snapshot.

## 7. Integrace se Spring Security
Oprávnění a role jsou mapovány na `GrantedAuthority`:

| Typ | Formát | Příklad |
|---|---|---|
| Role | `ROLE_<name>` | `ROLE_ADMIN` |
| Oprávnění | `PERM_<resource>:<action>` | `PERM_tenant.api-keys:read` |

`@EnableMethodSecurity` aktivuje autorizační kontroly přes `@PreAuthorize`.

Příklady:

```java
@PreAuthorize("hasAuthority('PERM_tenant.members:read')")
public ResponseEntity<?> listMembers() { ... }

@PreAuthorize("hasAnyAuthority('PERM_provider.tenants:create', 'PERM_provider.tenants:update')")
public ResponseEntity<?> upsertTenant() { ... }

@PreAuthorize("hasRole('API_KEY')")
public ResponseEntity<?> apiKeyEndpoint() { ... }
```

## 8. Administrace RBAC přes API

### 8.1 Oprávnění
- `GET /provider/permissions`
- `GET /provider/permissions/{id}`

Poznámka:
- write endpointy pro `permissions` jsou aktuálně v kontroleru vypnuté.

### 8.2 Role
- `GET /provider/roles`
- `GET /provider/roles/{id}`
- `POST /provider/roles`
- `PUT /provider/roles/{id}`
- `DELETE /provider/roles/{id}`

Kontrola přístupu je vynucena přes `PERM_provider.*` a vybraná `PERM_tenant.roles:*`.

## 9. Databázové tabulky
| Tabulka | Účel |
|---|---|
| `permissions` | Definice oprávnění |
| `roles` | Definice rolí |
| `role_permissions` | Vazba role ↔ oprávnění |
| `user_tenant_roles` | Vazba uživatel ↔ role ↔ tenant kontext |

Všechny tabulky používají soft-delete (`is_deleted`).

## 10. Související dokumentace
- [Autentizace](./authentication.md)
- [Multi-tenancy](./multi-tenancy.md)
- [Bootstrap sekvence](./bootstrap.md)
