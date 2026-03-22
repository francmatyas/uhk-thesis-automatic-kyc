# Nastavení úložiště MinIO S3

MinIO se používá jako lokální objektové úložiště kompatibilní se S3 pro vývoj.
Webová konzole je dostupná na **http://localhost:9001** (přihlášení: `minioadmin` / `minioadmin`).

## Požadavky

Kontejner MinIO musí běžet:

```bash
docker compose up -d minio
```

## Nastavení

Vytvoření bucketu a nastavení politiky se aplikuje automaticky při prvním `docker compose up` přes službu `minio-init`.
Žádné manuální kroky nejsou potřeba.

Ověření, že byla politika aplikována správně:

```bash
docker exec automatic_kyc_minio mc anonymous get local/automatic-kyc-localhost
```

## Model přístupu k souborům

| Kategorie | Prefix úložiště | Přístup |
|----------|---------------|--------|
| `AVATAR` | `public/documents/...` | Veřejně dostupné, není vyžadována autentizace |
| Vše ostatní | `private/documents/...` | Blokováno — vyžaduje presigned URL nebo přihlašovací údaje |

Formát URL veřejného souboru:
```
http://localhost:9002/automatic-kyc-localhost/public/documents/user/<userId>/<fileId>.jpg
```

## Užitečné příkazy

```bash
# Výpis všech objektů v bucketu
docker exec automatic_kyc_minio mc ls local/automatic-kyc-localhost --recursive

# Odstranění objektu
docker exec automatic_kyc_minio mc rm local/automatic-kyc-localhost/<key>

# Zobrazení politiky bucketu
docker exec automatic_kyc_minio mc anonymous get local/automatic-kyc-localhost
```
