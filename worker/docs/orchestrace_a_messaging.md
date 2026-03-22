# Orchestrace a messaging

## Role
Asynchronní worker služba nad RabbitMQ:
- přijímá úlohy z fronty dle typu workeru,
- průběžně publikuje stav zpracování,
- vrací finální výsledek ve stavech `succeeded`, `failed` nebo `cancelled`,
- podporuje externí zrušení běžící úlohy.

## Klíčové komponenty
- `src/main.py`: start služby, inicializace AMQP kontextu, registrace signálů `SIGINT/SIGTERM`, řízené ukončení.
- `src/amqp.py`: deklarace exchange, serializace/deserializace zpráv, publikace výsledků.
- `src/worker_runtime.py`: runtime workeru, odběr úloh a cancel zpráv, dispatch podle typu úlohy.

## AMQP topologie
Použité exchange:
- `x.jobs` (topic): příjem pracovních úloh.
- `x.results` (topic): publikace průběhu a výsledků.
- `x.control` (topic): řídicí zprávy (rušení úloh).
- `x.dlx` (topic): dead-letter exchange pro nerecoverovatelné chyby.

Použité fronty:
- pracovní fronta `q.worker.<WORKER_TYPE>` (durable, priority, DLX binding),
- dočasná cancel fronta `q.cancel.<worker_id>` (exclusive, auto-delete).

## Životní cyklus služby
1. Načtení konfigurace z prostředí (`AMQP_URL`, `WORKER_TYPE`, `PREFETCH`, případně `AML_DB_PATH`).
2. Navázání robustního AMQP spojení a deklarace exchange.
3. Inicializace `WorkerRuntime` a registrace consumerů:
- consumer úloh (`jobs.<worker_type>.#`),
- consumer zpráv o rušení (`cancel.job.*`, `cancel.worker.<worker_id>`).
4. Běh služby do obdržení terminačního signálu.
5. Korektní ukončení AMQP kanálu a spojení.

## Obecný proces zpracování úlohy
### Vstupní kontrakt zprávy
Worker očekává payload mapovaný na datovou strukturu `JobCommand`:
- `jobId`,
- `type`,
- `version`,
- `payload`,
- `timeoutMs`,
- `requestedAt`,
- `attempt`,
- `idempotencyKey` (volitelně).

Povinná je také hlavička `x-reply-to`. Bez této hlavičky je zpráva odmítnuta (`reject requeue=false`).

Podporované hodnoty `type`:
- `verify_czech_id`
- `verify_passport`
- `compare_faces`
- `liveness_check`
- `aml_screen`

Neznámý typ úlohy je ukončen chybou `UNKNOWN_JOB_TYPE`.

### Publikace průběhu a výsledku
Pro každou úlohu worker publikuje do `x.results` přes routing key:
`<x-reply-to>.<jobId>.<status>`

`status` je jedna z hodnot:
- `progress`,
- `succeeded`,
- `failed`,
- `cancelled`.

Průběh se publikuje minimálně ve stavech:
- `0 %` (`started`),
- `10 %` (začátek doménového výpočtu),
- `90 %` (dokončení doménového výpočtu).

### Chybové a terminační scénáře
- `succeeded`: výpočet proběhne bez chyby, zpráva je `ack`.
- `cancelled`: úloha je zrušena uživatelem nebo interně vyhodnocena jako zrušená, zpráva je `ack`.
- `failed`: interní chyba zpracování, výsledek se publikuje a zpráva je `reject requeue=false` (putuje do DLX).

Pro `failed` worker vrací pouze strukturu `error.code`:
- pokud je vyhozena výjimka `WorkerError`, propaguje se její kód (např. `MISSING_IMAGE_PATH`, `INVALID_MRZ_TYPE`, `MISSING_FULL_NAME`),
- u neznámé chyby se vrací `WORKER_ERR`.

## Proces rušení úloh
Rušení je realizováno asynchronně přes `x.control`:
- po přijetí `cancel` zprávy je `jobId` uložen do množiny zrušených úloh,
- pokud úloha právě běží, aktivuje se abort signalizace,
- doménové metody periodicky kontrolují stav zrušení (`is_cancelled`).

Pokud je zrušení potvrzeno během zpracování, worker vrací status `cancelled` s kódem chyby `CANCELLED`.
