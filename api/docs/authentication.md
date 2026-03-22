# Autentizace

## 1. Účel dokumentu
Tento dokument popisuje autentizační a související bezpečnostní mechanismy. Systém podporuje dva nezávislé způsoby autentizace:
- JWT cookie (uživatelská relace, typicky pro webové klienty),
- API klíč (server-to-server integrace).

## 2. Pořadí bezpečnostních filtrů
Každý požadavek prochází filtrovacím řetězcem v následujícím pořadí:

```text
AuthRateLimitFilter          → limituje /auth/login a /auth/register
ApiKeyAuthFilter             → autentizace přes X-API-Key + X-API-Secret
CookieJwtAuthFilter          → autentizace přes cookie AUTH_TOKEN
ProviderOnlyPathFilter       → ochrana cest /provider/*
TenantContextFilter          → určení aktivního tenanta
ProviderTenantAccessFilter   → ověření členství provider uživatele v tenantovi
```

Filtry buď:
- předají požadavek dále přes `chain.doFilter()`, nebo
- vrátí odpověď přímo a ukončí zpracování.

Autentizace je vzájemně exkluzivní: pokud `ApiKeyAuthFilter` nastaví `SecurityContext`, `CookieJwtAuthFilter` již autentizaci neprovádí.

## 3. JWT autentizace

### 3.1 Přihlášení
Endpoint `POST /auth/login` přijímá payload `{ email, password, rememberMe }`.

Průběh:
1. `AuthenticationManager` ověří přihlašovací údaje oproti BCrypt hashovanému heslu.
2. `PolicyService` sestaví aktuální `PolicySnapshot` (role a oprávnění).
3. Vytvoří se podepsaný JWT (HS256) s claims:
   - `sub` (UUID uživatele),
   - `jti` (ID relace pro revokaci),
   - `iss`, `iat`, `nbf`, `exp`,
   - `roles`, `permissions`, `policyVersion`,
   - `email`, `name`,
   - `tenantId` (pokud byl při přihlášení vybrán tenant).
4. JWT je uložen do cookie `AUTH_TOKEN` (`HttpOnly; Secure; SameSite=Lax`).
5. Vznikne záznam `UserSession` obsahující `jti`, expiraci, IP a informace o zařízení.

TTL relace je řízeno konfigurací:
- `app.jwt.access-ttl-minutes` (standardní relace),
- `app.jwt.remember-ttl-minutes` (remember-me, výchozí 7 dní).

### 3.2 Validace JWT při každém požadavku
Filtr `CookieJwtAuthFilter` provádí:
1. extrakci cookie `AUTH_TOKEN`,
2. ověření podpisu, vydavatele, expirace a `nbf` (s tolerancí 60 s),
3. kontrolu `jti` proti `user_sessions`,
4. načtení uživatele podle `sub`,
5. vyřešení tenant kontextu a sestavení čerstvého `PolicySnapshot`,
6. naplnění `SecurityContext` autoritami `ROLE_*` a `PERM_*`,
7. aktualizaci `UserSession.lastSeenAt`.

### 3.3 Odhlášení
`POST /auth/logout`:
- načte `AUTH_TOKEN`,
- extrahuje `jti`,
- označí `UserSession` jako revokovanou,
- smaže cookie (`maxAge=0`).

## 4. Autentizace API klíčem

### 4.1 Vytvoření klíče
`POST /integrations/api-keys` vrací dvojici:
- veřejný klíč `pk_...` (`X-API-Key`),
- secret `sk_...` (`X-API-Secret`, vrácen pouze jednou).

Secret je následně uložen pouze jako BCrypt hash.

### 4.2 Validace API klíče při požadavku
Filtr `ApiKeyAuthFilter`:
1. ověří přítomnost obou hlaviček (`X-API-Key`, `X-API-Secret`),
2. blokuje cesty `/auth/**` a `/users/**`,
3. načte aktivní klíč podle `publicKey`,
4. porovná secret přes `passwordEncoder.matches()`,
5. nastaví `SecurityContext` s `ApiKeyPrincipal` a autoritou `ROLE_API_KEY`,
6. aktualizuje `lastUsedAt`.

`ApiKeyPrincipal` obsahuje identitu klíče, nikoli entitu uživatele.

### 4.3 Přístup k endpointům
API klíč má přístup jen k endpointům označeným `@ApiKeyAccessible`.
Vynucení probíhá v `ApiKeyAccessInterceptor`.

## 5. CSRF ochrana
Pro uživatelské relace je aktivní cookie-based CSRF:
- cookie: `XSRF-TOKEN`,
- hlavička: `X-XSRF-TOKEN`.

Token lze získat přes `GET /auth/csrf`.

CSRF validace se nepoužije pro:
- `OPTIONS` preflight,
- veřejné cesty `/auth/**`,
- požadavky s API klíčem bez JWT cookie.

## 6. Veřejné endpointy bez autentizace

```text
GET  /auth/csrf
POST /auth/login
POST /auth/register
GET  /health
GET  /translations/**
GET  /images/**
```

## 7. Typy principálů v SecurityContext
| Metoda autentizace | Typ principála | Obsah |
|---|---|---|
| JWT cookie | `JwtPrincipal` | `User`, `JWTClaimsSet`, autority |
| API klíč | `ApiKeyPrincipal` | ID klíče, ID tenanta, název klíče |

Kontrolery používající `@AuthenticationPrincipal` musí počítat s rozdílnými typy principálů.

## 8. Omezení počtu požadavků na autentizační endpointy
`AuthRateLimitFilter` omezuje:
- `POST /auth/login`
- `POST /auth/register`

Politika:
- 10 požadavků / 10 minut / IP adresa,
- při překročení: `429 Too Many Requests` a `Retry-After: 600`.

## 9. Související dokumentace
- [API klíče](./api-keys.md)
- [Multi-tenancy](./multi-tenancy.md)
- [RBAC](./rbac.md)
