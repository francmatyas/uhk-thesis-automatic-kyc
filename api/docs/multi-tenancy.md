# Multi-tenancy

## 1. Účel dokumentu
Tento dokument popisuje, jak systém implementuje tenant izolaci na úrovni:
- bezpečnostního řetězce,
- aplikační logiky,
- databázových dotazů.

## 2. Typy uživatelů
Rozlišení probíhá přes `User.isProviderUser`.

### 2.1 Provider uživatel
- má přístup na `/provider/**`,
- může pracovat s více tenanty přepínáním aktivního kontextu (`X-Tenant-Id`),
- může mít provider role (`tenant_id = null`) i tenant role (`tenant_id = <uuid>`).

### 2.2 Tenant uživatel
- nemá přístup na `/provider/**`,
- pracuje pouze v tenant kontextu,
- používá tenant role navázané na aktivního tenanta.

## 3. Tenant context a jeho životní cyklus
Aktivní tenant je uložen v `TenantContext` (`ThreadLocal<UUID>`).

Životní cyklus:
1. `TenantContextFilter` nastaví tenant kontext,
2. služby a repozitáře čtou hodnotu z `TenantContext`,
3. ve `finally` bloku se kontext vždy vyčistí.

Důsledek:
- tenant kontext neuniká mezi požadavky sdílejícími stejné vlákno.

## 4. Rozlišení aktivního tenanta
Pro JWT autentizované požadavky se tenant určuje v pořadí:
1. hlavička `X-Tenant-Id`,
2. claim `tenantId` v JWT,
3. `null` (není zvolen tenant).

Toto rozlišení probíhá dvakrát:
- v `CookieJwtAuthFilter` (pro sestavení oprávnění),
- v `TenantContextFilter` (pro aplikační logiku).

Pro API klíč:
- tenant je pevně svázán s klíčem,
- nesoulad s `X-Tenant-Id` vede na `403 tenant_mismatch_for_api_key`.

## 5. Tenant izolace dat

### 5.1 Izolace na úrovni dotazů
Většina doménových entit je tenant-scoped přes `tenant_id`.
Služby a repozitáře filtrují data podle hodnoty v `TenantContext`.

### 5.2 Soft-delete vrstva
Entity používají `@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")`, což zajišťuje, že smazané záznamy nejsou standardně čitelné.

### 5.3 Vynucení členství provider uživatele
`ProviderTenantAccessFilter` ověřuje, že provider uživatel má přístup do zvoleného tenanta. Bez vazby uživatel-tenant je požadavek odmítnut (`403 not_member_of_tenant`).

## 6. Vazba na RBAC
`PolicyService.buildForUser()` čte `TenantContext` a skládá snapshot oprávnění podle aktivního kontextu:
- provider bez aktivního tenanta: pouze provider role,
- provider s tenantem: provider role + tenant role pro aktivního tenanta,
- tenant uživatel s tenantem: tenant role,
- tenant uživatel bez tenanta: prázdný snapshot.

Snapshot je sestavován při každém požadavku z aktuálního stavu databáze.

## 7. Ochrana `/provider/**`
`ProviderOnlyPathFilter` odmítne přístup na `/provider/**` pokud:
- požadavek není autentizovaný (`401`),
- nebo uživatel není provider (`403`).

Autentizace API klíčem provider přístup neuděluje.

## 8. Provozní doporučení

### 8.1 Asynchronní úlohy a scheduler
Vlákna mimo HTTP request nemají nastavený `TenantContext`. Pokud takové volání pracuje s tenant-scoped službou, je nutné kontext nastavit explicitně:

```java
try {
    TenantContext.setTenantId(tenantId);
    someService.doWork();
} finally {
    TenantContext.clear();
}
```

### 8.2 Unit testy
Při přímém volání služeb v testech je třeba `TenantContext` nastavit a po testu vyčistit.

### 8.3 Přepínání tenanta během relace
Provider uživatel může měnit aktivního tenanta po jednotlivých požadavcích bez nového přihlášení; oprávnění se přepočítávají dynamicky.

## 9. Chybové scénáře
| Stav | Typická příčina |
|---|---|
| `400 tenant_required` | Operace vyžaduje aktivního tenanta, ale kontext chybí |
| `403 not_member_of_tenant` | Uživatel nemá přístup do zvoleného tenanta |
| `403 tenant_mismatch_for_api_key` | `X-Tenant-Id` neodpovídá tenantovi API klíče |

## 10. Související dokumentace
- [Autentizace](./authentication.md)
- [RBAC](./rbac.md)
