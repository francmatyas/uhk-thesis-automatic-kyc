# Automatic KYC

Monorepo pro systém automatizovaného KYC ověření s těmito hlavními částmi:
- `api`: backend (Spring Boot), orchestrace verifikací, RBAC, audit logy, webhooky.
- `worker`: asynchronní zpracování kontrol (doklady, biometrie, AML) přes RabbitMQ.
- `admin`: administrátorské rozhraní pro správu tenantů, uživatelů a KYC agendy.
- `verify`: klientská verifikační aplikace pro koncového uživatele (`/v/:token`).
- `ops`: provozní artefakty (TLS/mTLS certifikáty, RabbitMQ konfigurace).

## Rychlý rozcestník
- [Tutoriál: init systému](docs/system-init-tutorial.md)
- [Popis fungování systému](docs/system-runtime-flow.md)
- [API dokumentace](api/README.md)
- [Admin dokumentace](admin/README.md)
- [Worker dokumentace](worker/README.md)
- [Verify dokumentace](verify/README.md)
- [TLS/mTLS setup](ops/README.md)
- [MinIO setup](docs/minio-setup.md)

## Rychlý start (lokální)
1. Připravte prostředí:
```bash
cp .env.example .env
```
2. Připravte TLS/mTLS certifikáty a Java keystores podle [ops/README.md](ops/README.md).
3. Spusťte stack:
```bash
docker compose up -d --build
```
4. Otevřete aplikace:
- `http://admin.localhost`
- `http://verify.localhost`
- `http://api.localhost`
- `http://localhost:15672` (RabbitMQ UI)
- `http://localhost:8088` (Traefik dashboard)

Detailní postup je v [docs/system-init-tutorial.md](docs/system-init-tutorial.md).
