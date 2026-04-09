# API kontrakt a integrace ve Verify

## 1. Základní konfigurace klienta
Soubor: `src/api/verification.js`

- `baseURL`: `VITE_API_BASE_URL` (default prázdné, tedy stejný origin).
- `BASE_PATH`: `VITE_API_BASE_PATH` (default `/flow/verify/v1`).
- timeout: 10 s.
- `withCredentials: true` kvůli cookie-based security.

## 2. CSRF chování
- Každý nemutační request (`GET`) běží standardně.
- Každý mutační request (`POST`) nejprve načte nový CSRF token z `GET /auth/csrf`.
- Header/token se nastaví ručně v request interceptoru (kvůli Spring Security maskování tokenu).

## 3. Endpointy používané frontendem
- `GET /flow/verify/v1/{token}`
- `POST /flow/verify/v1/{token}/email/send-code`
- `POST /flow/verify/v1/{token}/email/verify-code`
- `POST /flow/verify/v1/{token}/phone/send-code`
- `POST /flow/verify/v1/{token}/phone/verify-code`
- `POST /flow/verify/v1/{token}/personal-info`
- `POST /flow/verify/v1/{token}/documents/presign`
- `POST /flow/verify/v1/{token}/documents/{documentId}/complete`
- `POST /flow/verify/v1/{token}/id-document`
- `POST /flow/verify/v1/{token}/liveness`
- `POST /flow/verify/v1/{token}/aml`
- `POST /flow/verify/v1/{token}/finalize`

Backend reference: `api/.../verification/controller/FlowController.java`.

## 4. Tok nahrávání dokumentů a liveness
Pro binární data frontend neposílá soubory přímo do API. Používá 3fázový tok:

1. `presign`: API vrátí dočasné upload URL.
2. `PUT` upload přímo do objektového úložiště.
3. `complete`: API potvrdí dokončení uploadu.

Teprve potom frontend odešle doménový payload:
- `id-document`: vazba typu dokladu + ID nahraných souborů,
- `liveness`: seznam snímků s pozicí (`center/left/right/up`).

## 5. Mapování kroků a typů dokladů
- API typy dokladů:
- `CZECH_ID`
- `PASSPORT`
- UI typy:
- `id_card`
- `passport`

Mapování je obousměrné v `verification.js`:
- `API_TO_UI_DOCUMENT_TYPE`
- `DOCUMENT_TYPE_MAP`

## 6. Chybové kódy a očekávané reakce UI
Frontend explicitně rozlišuje:
- `verification_already_submitted`
- `verification_expired`
- `not_found`

Další typické statusy z API:
- `400` (validace/argument),
- `403` (security),
- `409` (invalid finalize state),
- `410` (expired/gone),
- `422` (OTP doménové chyby).

## 7. Závislosti na backend konfiguraci
Pro správné fungování verify flow musí API mít:
- správné CORS pro verify origin (`CORS_ALLOWED_ORIGINS`),
- konzistentní `KYC_FLOW_BASE_URL` (pro generování odkazů),
- funkční storage endpoint (`S3_ENDPOINT`, `S3_WORKER_PRESIGN_ENDPOINT`, `S3_PUBLIC_BASE_URL`).

