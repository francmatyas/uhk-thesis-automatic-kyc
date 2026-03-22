# Provozní vrstva

## Konfigurace prostředí
Relevantní proměnné prostředí:
- `AMQP_URL`: adresa RabbitMQ,
- `WORKER_TYPE`: logický typ workeru,
- `PREFETCH`: QoS limit neackovaných zpráv,
- `AML_DB_PATH`: cesta k AML databázi (volitelně, default `data/aml.db`).

## Provozní charakteristiky
- Worker je navržen jako single-process, async konzument.
- CPU-intenzivní doménové operace jsou spouštěny přes `run_in_executor`, aby neblokovaly event loop.
- Výsledky jsou publikovány asynchronně (`fire-and-forget`) do `x.results`.
- Selhané úlohy bez requeue jsou směrovány do DLX, což umožňuje následnou auditní analýzu.

## Chybový model
- Modul `src/errors.py` zavádí výjimku `WorkerError(code)`.
- Worker při selhání vrací strukturu `error.code`; textový `error.message` je používán jen u stavu `cancelled`.
- Kódy chyb pokrývají:
  - validační chyby payloadu (`MISSING_BACK_IMAGE_PATH`, `MISSING_IMAGE_PATH`, `MISSING_DOCUMENT_PATH`, `MISSING_SELFIE_PATH`, `MISSING_IMAGE_PATHS`, `MISSING_FULL_NAME`),
  - chyby čtení a typu dokladu (`IMAGE_READ_ERROR`, `INVALID_MRZ_TYPE`, `INVALID_DOCUMENT_COUNTRY`, `MRZ_NOT_FOUND`),
  - závislosti a data (`AML_DB_NOT_FOUND`),
  - orchestrace (`UNKNOWN_JOB_TYPE`, fallback `WORKER_ERR`).

## Omezení a rizika
- Kvalita výsledku závisí na kvalitě vstupních snímků (rozlišení, ostrost, komprese, osvětlení).
- Liveness detekce je heuristická (orientace hlavy), nikoliv plnohodnotná anti-spoofing biometrie.
- AML screening je kandidátní párování nad jmény; bez dalších atributů může produkovat falešně pozitivní shody.
- Automatické stahování modelů při prvním použití vyžaduje síťovou dostupnost.
