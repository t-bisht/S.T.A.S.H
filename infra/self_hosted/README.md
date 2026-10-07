# Stash — self-hosted deployment

Compose stack for running Stash on the local network.

## Layout

```
self_hosted/
├── docker-compose.yml        # root — networks, volumes, includes
├── compose/
│   └── postgres.yml          # Postgres 16 (alpine)
├── pg-init/                  # one-shot schema bootstrap (see pg-init/README.md)
├── .env.example              # copy to .env, fill in secrets
└── README.md
```

## Port convention

All host-exposed ports in this project live in the **7xxx** range (8xxx is
reserved for other apps running on the host).

| Service       | Host port | Container port |
|---------------|-----------|----------------|
| stash-postgres | 7432      | 5432           |

## First-time setup

```bash
cd infra/self_hosted
cp .env.example .env            # then edit PG_PASSWORD
docker compose up -d
docker compose ps               # verify stash-postgres is "healthy"
```

Connect from host:

```bash
psql -h 127.0.0.1 -p 7432 -U stash -d stash
```

## Data persistence

Postgres data lives in the named volume `stash-postgres-data` (declared in the
root `docker-compose.yml`). It survives:

- container crashes / restarts
- `docker compose down`
- `docker compose stop` / `start`
- host reboots

It is destroyed **only** by an explicit `docker compose down -v`.

## Common commands

```bash
docker compose up -d                    # start stack
docker compose down                     # stop (data kept)
docker compose logs -f stash-postgres   # tail logs
docker compose pull                     # refresh images
docker compose down -v                  # ⚠ stop + wipe all volumes
```

## Adding a new service

1. Create `compose/<service>.yml`.
2. Add `- compose/<service>.yml` to the `include:` block of `docker-compose.yml`.
3. Pick a host port in `7xxx` (see table above; keep it unique).
4. Attach to `stash-backend-net` (or declare a new network in root compose).
