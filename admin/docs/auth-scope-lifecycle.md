# Životní cyklus autentizace a scope ve frontendové aplikaci

## 1. Účel dokumentu

Tento dokument systematicky popisuje architekturu a provozní chování autentizační vrstvy, autorizační logiky a směrování podle scope (`provider`, `tenant`) ve frontendové aplikaci. Cílem je vymezit odpovědnosti jednotlivých modulů, formalizovat datové toky a zpřehlednit rozhodovací logiku při přepínání pracovního kontextu uživatele.

## 2. Rozsah a relevantní artefakty

Dokument pokrývá následující soubory:

- `src/contexts/AuthContext.jsx`
- `src/router/ScopeGuards.jsx`
- `src/router/scope.js`
- `src/router/authRedirect.js`
- `src/api/axiosInstance.js`
- `src/api/auth.js`
- `src/api/tenants.js`
- `src/components/auth/LoginForm.jsx`
- `src/components/sidebar/SidebarTenantSwitch.jsx`

## 3. Terminologie

- `provider scope`: provozní kontext reprezentovaný URL prefixem `/p`
- `tenant scope`: provozní kontext reprezentovaný URL prefixem `/t/:tenantSlug`
- `global scope`: trasy bez scope prefixu, typicky veřejné autentizační stránky
- `activeTenantId`: identifikátor aktuálně aktivního tenantu v aplikačním stavu

## 4. AuthContext jako centrální zdroj aplikační identity

Komponenta `AuthProvider` v `AuthContext.jsx` představuje centrální bod správy identity a autorizačního kontextu. Poskytuje zejména následující stavové a odvozené hodnoty:

- `user`
- `preferences`
- `tenants`
- `activeTenantId`
- `permissions` (datová struktura `Set`)
- `roles` (datová struktura `Set`)
- `activeScope`
- `routeTenantSlug`

### 4.1 Inicializační sekvence

Při změně URL probíhá následující rozhodovací mechanismus:

1. Je-li aktuální trasa veřejná (`/login`, `/register`, `/forgot-password`), inicializace se označí za dokončenou bez volání profilového endpointu.
2. Pokud je `user` již dostupný v paměti klienta, inicializace se označí za dokončenou.
3. V opačném případě se provede `fetchProfile()` (`GET /users/me`) a získaná data se promítnou do kontextu.
4. Render chráněné části aplikace je podmíněn stavem `initialized`.

Tento postup minimalizuje riziko nekonzistentního UI během obnovy session po načtení stránky.

### 4.2 Role a oprávnění

Vstupní pole `permissions` a `roles` jsou při nastavování profilu i při přepnutí scope převáděna do datové struktury `Set`. Před převodem je aplikována základní sanitizace hodnot (`filter(Boolean)`).

Autorizační vyhodnocení je realizováno pomocí:

- `hasPermission(permission, { mode: "any" | "all" })`
- `hasRole(role, { mode: "any" | "all" })`

Tento přístup podporuje jak disjunktivní, tak konjunktivní vyhodnocení požadavků.

## 5. Model scope routingu

Soubor `src/router/scope.js` definuje pravidla mapování URL na provozní kontext a zpětné skládání scoped tras.

### 5.1 Základní pravidla

- tenant prefix: `/t`
- provider prefix: `/p`

### 5.2 Klíčové utility

- `getScopeFromPath(pathname)`
- `getTenantSlugFromPath(pathname)`
- `stripScopePrefix(pathname)`
- `toProviderPath(innerPath)`
- `toTenantPath(tenantSlug, innerPath)`
- `getSwitchTargetPath({ currentPath, targetScope, targetTenantSlug })`

### 5.3 Omezení vnitřních tras

Přepínání scope je řízeno allowlisty:

- `TENANT_ROUTE_PREFIXES`
- `PROVIDER_ROUTE_PREFIXES`

Pokud aktuální `innerPath` není v cílovém scope povolen, je použita fallback cesta na domovskou trasu cílového scope.

## 6. Guard mechanismy pro scope

Soubor `src/router/ScopeGuards.jsx` implementuje synchronizaci klientského stavu s autorizačním kontextem serveru.

### 6.1 `ScopeLandingRedirect`

Komponenta vyhodnocuje výchozí cílovou trasu po vstupu na `/`:

1. tenant odpovídající `activeTenantId`
2. první tenant se validním `slug`
3. provider home (`/p`)

Pokud uživatel není autentizován, následuje přesměrování na `/login`.

### 6.2 `ProviderScopeGuard`

Mechanismus guardu:

1. U neautentizovaného uživatele přesměruje na login a předá původní URL v parametru `redirect`.
2. Při aktivním tenant kontextu vyvolá `switchTenantScope({ tenantId: null })`.
3. Odpověď aplikuje přes `applyTenantSwitch(payload)`.

Mapování chybových stavů:

- `401`: okamžité přesměrování na login
- `403`: zobrazení stavu bez oprávnění
- ostatní: obecná chyba inicializace scope

### 6.3 `TenantScopeGuard`

Mechanismus guardu:

1. U neautentizovaného uživatele přesměruje na login s parametrem `redirect`.
2. Ověří tenant kontext přes `resolveTenantBySlug(tenantSlug)`.
3. Pokud se resolved tenant liší od `activeTenantId`, provede `switchTenantScope({ tenantSlug })`.
4. Výsledek propíše do kontextu voláním `applyTenantSwitch(...)`.

Mapování chybových stavů:

- `401`: přesměrování na login
- `403` nebo `404`: stav bez přístupu
- ostatní: obecná chyba inicializace tenant scope

## 7. Validace post-login redirectu

Soubor `src/router/authRedirect.js` omezuje redirect cíle z bezpečnostních důvodů:

- redirect musí být lokální absolutní cesta začínající `/`
- redirect nesmí začínat `//`
- provider cesty (`/p...`) jsou povoleny pouze provider uživatelům
- tenant cesty (`/t/:slug...`) jsou povoleny pouze pro tenanty přítomné v uživatelském profilu

## 8. Axios interceptory pro autentizaci a CSRF

V `src/api/axiosInstance.js` je implementována centrální strategie pro CSRF token a reakci na auth chyby.

### 8.1 Request interceptor

Před odesláním requestu (mimo CSRF endpoint) je načtena CSRF konfigurace z `/auth/csrf`, následně jsou doplněny příslušné hlavičky:

- backendem definovaná hlavička
- kompatibilitní hlavička `X-XSRF-TOKEN`

### 8.2 Response interceptor

- Na auth lifecycle endpointech (`/auth/login`, `/auth/register`, `/auth/logout`) dochází k invalidaci CSRF cache.
- U mutačních requestů (`POST`, `PUT`, `PATCH`, `DELETE`) s odpovědí `403` nebo `419` je proveden jednorázový retry s obnoveným CSRF tokenem.
- U `401` mimo auth endpointy je aplikováno přesměrování na login.

## 9. API vrstva pro tenant scope operace

Soubor `src/api/tenants.js` poskytuje dvě klíčové operace:

- `resolveTenantBySlug(slug)` -> `GET /tenants/resolve/:slug`
- `switchTenantScope(payload)` -> `POST /tenants/switch`

Obě operace používají deduplikaci souběžných požadavků pomocí in-memory map (`Map`) a tím snižují riziko redundantních requestů při rychlých změnách navigace.

## 10. UI vrstva přepínání pracovního kontextu

Komponenta `SidebarTenantSwitch` používá `getSwitchTargetPath` a při přechodu mezi scope zachovává:

- query parametry (`search`)
- URL fragment (`hash`)

Inner route je zachována pouze tehdy, je-li validní v cílovém scope.

## 11. Veřejné trasy a výjimky z profilové inicializace

Za veřejné trasy jsou v současné implementaci považovány:

- `/login`
- `/register`
- `/forgot-password`

Při rozšíření veřejné části aplikace je nutné aktualizovat tento seznam v `AuthContext`.

## 13. Doporučené navazující dokumenty

- `docs/table-data-contract.md` pro formalizaci kontraktu mezi tabulkovou frontend vrstvou a backend API.
- `docs/module-definitions.md` pro standardizaci schématu module definitions.
