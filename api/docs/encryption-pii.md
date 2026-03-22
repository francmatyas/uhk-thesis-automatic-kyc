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

### 5.1 Entita `users`
| Pole | Typ | Poznámka |
|---|---|---|
| `givenName` | String | Křestní jméno |
| `middleName` | String | Prostřední jméno |
| `familyName` | String | Příjmení |
| `fullName` | String | Plné jméno |
| `email` | String | Primární e-mail |

### 5.2 Entita `user_profiles`
| Pole | Typ |
|---|---|
| `phoneNumber` | String |
| `dialCode` | String |
| `gender` | String |
| `dateOfBirth` | Instant |

### 5.3 Entita `user_sessions`
| Pole | Typ |
|---|---|
| `ipAddress` | String |
| `userAgent` | String |

### 5.4 Entita `webhook_endpoints`
| Pole | Typ | Poznámka |
|---|---|---|
| `secret` | String | Secret pro podpis webhook zpráv |

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

## 8. Bezpečnostní a regulatorní dopady
Šifrování na úrovni polí minimalizuje riziko úniku PII při:
- přímém přístupu k databázi,
- práci se zálohami,
- analytických exportech.

Klíč je nutné provozně oddělit od databáze, ideálně pomocí dedikovaného správce tajemství.

## 9. Související dokumentace
- [Autentizace](./authentication.md)
- [Webhooky](./webhooks.md)
