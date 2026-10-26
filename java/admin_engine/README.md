# admin_engine — admin console + catalog management

Spring Boot 3.3.4 / Java 21 service for the STASH admin module. It is the authoritative
correction surface for the product-level catalog (`digital_shopping_mart_catalog`): a human
operator reviews LLM-assigned classifications, reassigns them, and the change cascades into
historical spend rows — all audited.

Design specs (the ground truth) live in the sibling repo:
`../arthasetu/stash/design/admin_module/` (`admin_module_design.md` + `backend/`).

- **Port:** `7084` (host + container; Stash 7xxx convention). In compose it is bound to
  **loopback only** (`127.0.0.1:7084`) — accidental internet exposure is structurally impossible.
- **Auth:** static username + password from env (`STASH_ADMIN_USERNAME` / `STASH_ADMIN_PASSWORD`),
  opaque server-side session cookie `STASH_ADMIN_SESSION` (in-process, 8h TTL). No OAuth, no MFA,
  no rate limiting in v1 — acceptable only because of the loopback binding.
- **DB:** the shared `stash` Postgres. `admin_engine` talks to Postgres only — it never calls
  `user_engine`'s `/internal/**`, never reads `user_oauth_accounts`/`sessions`, never joins
  `tb-shared`.

## Architecture

```
browser → 127.0.0.1:7084 ─┬─ /                static web_admin bundle (src/main/resources/static/)
                          ├─ /admin/login     credential check → STASH_ADMIN_SESSION cookie
                          ├─ /admin/api/**     session-guarded REST (SessionInterceptor)
                          │     catalog review + PATCH (UPDATE + cascade + audit, one tx)
                          │     taxonomy read + add
                          │     audit log read
                          └─ /actuator/**     health + metrics (no session; loopback-protected)
                                   │
                                   ▼
                          shared stash Postgres
```

Feature packages follow the `controller/service/crud/domain/exception` split; data classes are
`*Persistence` (hand-written `NamedParameterJdbcTemplate`), never `*Repository`. The catalog PATCH
runs the catalog `UPDATE`, the cascade `UPDATE` into `online_retail_purchase_history`, and the
`admin_audit_log` `INSERT` in **one transaction** — the audit row can never be absent for a
committed edit (design §6–§7).

## Build & run

The admin UI bundle is served by this service from `src/main/resources/static/`. **That directory
is build output and is gitignored** — it must be populated before `bootJar`, or the image serves a
404 at `GET /`. `docker_build.sh` does **not** build or copy the UI; do it first:

```bash
# 1. Build the admin UI and copy it into this service's static resources (from repo root):
cd web_admin && npm run build
rm -rf ../java/admin_engine/src/main/resources/static && \
  cp -R dist ../java/admin_engine/src/main/resources/static

# 2. Build / run the service (from java/):
cd ../java
./gradlew :admin_engine:bootRun          # run in dev (needs the DB on :7432)
./gradlew :admin_engine:test             # unit + Testcontainers integration tests (needs Docker)
./gradlew format                         # Spotless across all subprojects
./docker_build.sh admin_engine           # bootJar + image obake/stash-admin-engine (build UI first!)
```

`STASH_ADMIN_USERNAME` / `STASH_ADMIN_PASSWORD` must be set (both `@NotBlank`) or startup fails.

## Database migration

`infra/self_hosted/pg-init/07_admin_catalog.sql` creates `digital_shopping_mart_catalog` and
`admin_audit_log`. It runs automatically on an empty volume; apply by hand to an existing volume
(idempotent):

```bash
psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/07_admin_catalog.sql
```

## API — curl (all 9 endpoints + actuator)

```bash
# 1. Login
curl -c /tmp/admin.jar -s -X POST http://127.0.0.1:7084/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"change-me"}'

# 2. Logout
curl -b /tmp/admin.jar -s -X POST http://127.0.0.1:7084/admin/logout

# 3. Me (session probe / UI bootstrap)
curl -b /tmp/admin.jar -s http://127.0.0.1:7084/admin/api/me

# 4. Taxonomy tree
curl -b /tmp/admin.jar -s http://127.0.0.1:7084/admin/api/taxonomy | jq .

# 5. Add category
curl -b /tmp/admin.jar -s -X POST http://127.0.0.1:7084/admin/api/categories \
  -H "Content-Type: application/json" -d '{"categoryName":"Nuts & Seeds"}'

# 6. Add subcategory
curl -b /tmp/admin.jar -s -X POST http://127.0.0.1:7084/admin/api/subcategories \
  -H "Content-Type: application/json" \
  -d '{"categoryId":2,"subcategoryName":"Nuts & Trail Mix"}'

# 7. Catalog (filtered, spend-weighted)
curl -b /tmp/admin.jar -s \
  "http://127.0.0.1:7084/admin/api/catalog?status=needs_review&sort=total_spend_desc&limit=10" \
  | jq .

# 8. Edit catalog row (reassign subcategory; cascades to history)
curl -b /tmp/admin.jar -s -X PATCH http://127.0.0.1:7084/admin/api/catalog/44 \
  -H "Content-Type: application/json" -d '{"subcategoryId":4}'

# 9. Audit log
curl -b /tmp/admin.jar -s \
  "http://127.0.0.1:7084/admin/api/audit?limit=20" | jq .

# Actuator health (no session required — loopback protects it, not auth)
curl -s http://127.0.0.1:7084/actuator/health | jq .

# Micrometer counters (appear only after the first event)
curl -s http://127.0.0.1:7084/actuator/metrics/stash.admin.catalog_edit | jq .
curl -s "http://127.0.0.1:7084/actuator/metrics/stash.admin.catalog_edit?tag=action:catalog.edit_subcategory" | jq .
curl -s http://127.0.0.1:7084/actuator/metrics/stash.admin.auth_failure | jq .
curl -s http://127.0.0.1:7084/actuator/metrics/stash.admin.taxonomy_add | jq .
```

JSON is **camelCase** on the wire (snake_case only for DB columns and the opaque `sort` / `action`
tokens). `totalSpend` is a 2-dp decimal (`BigDecimal`), never a float. Errors are flat
`{"code","message"}` with codes: `unauthorized | not_found | duplicate_name | validation_error |
invalid_subcategory | internal_error`.

## Deliberate v1 deferrals

These are conscious trade-offs (design §10), recorded so they do not silently become permanent:

- **No `Secure` cookie flag** — plain HTTP on loopback (`session.secure: false`); re-enable with TLS.
- **No dedicated least-privilege DB role, no dedicated admin Docker network** — single operator,
  LAN only.
- **No Google OAuth / MFA / login rate limiting** — static credentials are acceptable only while
  the loopback binding holds.
- **In-process session store** — a process restart clears all sessions (no reap thread; at most one
  operator). Move to a Postgres-backed store for rolling deploys / HA.
- **Actuator on the main port, reachable without a session** — the loopback binding protects it,
  not authentication. Securing `/actuator/**` is required before any non-loopback exposure.
- **UI bundle copy is manual** (`web_admin/dist/` → `static/`), run before `bootJar`; skipping it
  ships an image that 404s at `GET /`. `docker_build.sh` does not do this step.
- **No expression index** on the normalised `item_name` cascade match — negligible at current
  volume; add when `online_retail_purchase_history` grows (see `07_admin_catalog.sql` header).
