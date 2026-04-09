# Dokumentace Verify aplikace

Klientská aplikace `uhk-thesis-automatic-kyc-verify` je frontend pro koncové uživatele, kteří dokončují KYC ověření přes jednorázový token (`/v/:token`).

## Účel a rozsah
- načtení verifikačního flow z API,
- průchod povinnými/volitelnými kroky podle konfigurace journey template,
- sběr a upload podkladů (doklad, liveness),
- finalizace flow po dokončení všech povinných kroků.

## Rozcestník dokumentace
- [Architektura a řízení flow](docs/architektura-flow.md)
- [API kontrakt a integrace](docs/api-kontrakt.md)

## Lokální spuštění
```bash
npm ci
npm run dev
```

Build:
```bash
npm run build
npm run preview
```

## Konfigurace
Hlavní proměnné:
- `VITE_API_BASE_URL`: base URL API (v produkci/build kontejneru).
- `VITE_API_BASE_PATH`: cesta flow API, default `/flow/verify/v1`.
- `VITE_FACE_API_MODELS_URL`: cesta k `face-api.js` modelům, default `/models/face-api`.
- `VITE_API_URL`: dev proxy target pro `/flow` a `/auth` (viz `vite.config.js`).

## Hlavní technické body
- React Router (`/`, `/v/:token`) a React Query pro načítání flow a mutace kroků.
- i18n přes `i18next` (`cs`, `en`) s YAML překlady v `public/locales`.
- Dokumenty a liveness používají presigned upload do objektového úložiště.
- Liveness obsahuje lokální detekci obličeje (`face-api.js`) s fallbackem na ruční snímek.
