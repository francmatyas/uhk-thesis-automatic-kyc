# Šifrování a ochrana PII

## 1. Účel dokumentu
Dokument popisuje způsob ochrany osobních údajů (PII), zejména:
- šifrování dat na úrovni databázových polí,
- správu kryptografických klíčů,
- vyhledávání podle e-mailu bez plošného dešifrování.

## 2. Kryptografický model

### 2.1 Použitý algoritmus
- šifra: `AES/GCM/NoPadding`,
- délka klíče: 256 bitů,
- délka autentizačního tagu: 128 bitů,
- IV (nonce): 12 bajtů, náhodně generované pro každé šifrování.

### 2.2 Formát uložené hodnoty
Šifrované hodnoty jsou uloženy ve formátu:

```text
enc:v1:BASE64(IV):BASE64(CIPHERTEXT)
```

Vlastnosti:
- každé šifrování používá unikátní IV,
- shodné vstupy produkují odlišné ciphertexty,
- prefix `enc:v1:` slouží pro verzování formátu.

## 3. Správa klíčů

### 3.1 Odvození klíčů
Ze společného master secret jsou odvozeny dva oddělené klíče:

| Klíč | Odvození | Účel |
|---|---|---|
| Šifrovací klíč | `SHA-256(master + "enc:v1")` | AES-GCM šifrování a dešifrování |
| Hashovací klíč | `SHA-256(master + "hash:v1")` | HMAC-SHA256 hash e-mailu |

### 3.2 Konfigurace

```text
APP_ENCRYPTION_MASTER_KEY=<náhodný řetězec, min. 32 znaků>
```

Mapování:
- env proměnná `APP_ENCRYPTION_MASTER_KEY`,
- aplikační property `app.encryption.master-key`.

Inicializace:
- při startu aplikace `FieldCryptoBootstrap` inicializuje `FieldCrypto`.

### 3.3 Chování při chybějícím klíči
- produkční režim: chybějící klíč je fatální chyba startu,
- vývojové prostředí: používá se fallback hodnota.

## 4. Transparentní šifrování přes JPA konvertory

### 4.1 `EncryptedStringConverter`
Určen pro textová pole:
- `convertToDatabaseColumn()` šifruje přes `FieldCrypto.encrypt()`,
- `convertToEntityAttribute()` dešifruje přes `FieldCrypto.decrypt()`.

Příklad:

```java
@Convert(converter = EncryptedStringConverter.class)
private String phoneNumber;
```

### 4.2 `EncryptedInstantConverter`
Určen pro časové údaje typu `Instant`:
- ukládá hodnotu jako šifrovaný ISO-8601 text,
- při dešifrování podporuje i starší formát pro zpětnou kompatibilitu.

Příklad:

```java
@Convert(converter = EncryptedInstantConverter.class)
private Instant dateOfBirth;
```

## 5. Přehled šifrovaných polí
Šifrovaná pole jsou definována přes `@Convert(...)` v entitách. Níže je praktický přehled hlavních domén:

| Doména | Příklady šifrovaných polí |
|---|---|
| Uživatelé | `users.givenName`, `users.middleName`, `users.familyName`, `users.fullName`, `users.email` |
| Profil a sessions | `user_profiles.phoneNumber`, `user_profiles.dialCode`, `user_profiles.gender`, `user_profiles.dateOfBirth`, `user_sessions.ipAddress`, `user_sessions.userAgent` |
| Tenant a identita klienta | `tenants.name`, více polí v `client_identities` (jméno, doklady, adresy, narození apod.) |
| Dokumenty | `stored_documents.storage_key`, `stored_documents.original_filename` |
| Integrace/webhooky | `webhook_endpoints.url`, `webhook_endpoints.secret`, `webhook_delivery_jobs.event_payload`, `webhook_delivery_attempts.response_body` |
| Audit | `audit_logs.old_value`, `audit_logs.new_value`, `audit_logs.ip_address`, `audit_logs.user_agent` |
| Worker/KYC data | `worker_jobs.payload/result/error`, `check_results.details_json`, `verification_otps.contact` |

Pro úplný aktuální seznam je směrodatné vyhledání `@Convert(converter = Encrypted...)` v kódu.

## 6. Vyhledávání e-mailu bez dešifrování
Pro efektivní lookup se ukládá deterministický HMAC hash:

```text
emailHash = HmacSHA256(hashKey, normalize(email))
normalize = trim + lowercase(Locale.ROOT)
```

`emailHash` je indexován (unikátní index), takže `UserEmailLookupService.findByEmail()` nevyžaduje plošné dešifrování.

### 6.1 Zpětná kompatibilita
U starších záznamů může být e-mail uložen nešifrovaně. Služba nejprve zkusí `emailHash` a při neúspěchu fallback na legacy porovnání.

## 7. Databázové migrace
| Migrace | Účel |
|---|---|
| `V4__users_encrypted_fields_and_email_hash.sql` | Přidání `email_hash`, úprava sloupců uživatele |
| `V11__encrypt_pii_fields.sql` | Rozšíření sloupců v `user_profiles` a `user_sessions` na `text` |
| `V12__encrypt_sensitive_fields.sql` | Rozšíření šifrování citlivých dat v dalších modulech |
| `V13__encrypt_audit_log_values.sql` | Šifrování vybraných hodnot v audit logu |

## 8. Bezpečnostní a regulatorní dopady
Šifrování na úrovni polí minimalizuje riziko úniku PII při:
- přímém přístupu k databázi,
- práci se zálohami,
- analytických exportech.

Klíč je nutné provozně oddělit od databáze, ideálně pomocí dedikovaného správce tajemství.

## 9. Související dokumentace
- [Autentizace](./authentication.md)
- [Webhooky](./webhooks.md)
