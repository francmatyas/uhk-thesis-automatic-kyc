# Dokumentace procesů KYC workeru

## Účel a rozsah
Tato dokumentace popisuje procesní a technickou logiku služby `uhk-thesis-automatic-kyc-worker` na úrovni implementace.

## Hlavní části systému
- Orchestrace a messaging (`main.py`, `amqp.py`, `worker_runtime.py`)
- Chybový model workeru (`src/errors.py`)
- Zpracování dokladů (`src/document/*`)
- Biometrie (`src/biometrics/*`)
- AML screening (`src/aml/*`)
- Provozní vrstva (konfigurace, lifecycle, chybové scénáře)

## Rozcestník
- [orchestrace_a_messaging.md](./docs/orchestrace_a_messaging.md)
- [zpracovani_dokladu.md](./docs/zpracovani_dokladu.md)
- [biometrie.md](./docs/biometrie.md)
- [aml_screening.md](./docs/aml_screening.md)
- [provozni_vrstva.md](./docs/provozni_vrstva.md)

## Závěr
KYC worker implementuje modulární pipeline pro verifikaci identity (občanský průkaz i pas), biometrickou kontrolu a AML screening s jednotným komunikačním rozhraním přes RabbitMQ. Architektura odděluje orchestrace úloh od doménové logiky, podporuje průběžné reportování stavu i řízené rušení úloh a používá explicitní chybové kódy pro známé validační a provozní stavy.
