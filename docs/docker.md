# Docker Runtime Guide

This repository uses a root-canonical Docker setup.

## Canonical Files
- `api/Dockerfile`
- `web/Dockerfile`
- `docker-compose.yml` (base)
- `docker-compose.local.yml`
- `docker-compose.dev.yml`
- `docker-compose.prod.yml`
- `.env` / `.env.example`

Root compose is canonical. Service Dockerfiles live inside `api/` and `web/`.

## Run Commands
- Local:
  - `docker compose -f docker-compose.yml -f docker-compose.local.yml up --build`
- Dev-like:
  - `docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build`
- Prod-like:
  - `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`

## How Migrations Run
- `flyway` service runs at stack start.
- Migration source path:
  - `api/src/main/resources/db/migration`
- API starts only after:
  - PostgreSQL healthcheck is healthy
  - Redis healthcheck is healthy
  - Flyway migration container completed successfully

To add a migration:
1. Add a file in `api/src/main/resources/db/migration` named `V{number}__description.sql`.
2. Restart stack with compose command.
3. Flyway applies the new migration automatically.

## Caching Strategy
- API image:
  - Maven dependencies cached in a dedicated build layer (`pom.xml` copied before source).
- Web image:
  - npm dependencies cached by lockfile layer (`package-lock.json` copied before source).

## Notes
- Hibernate schema mutation is disabled in containers:
  - `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- Keep secrets in `.env` outside versioned defaults.
