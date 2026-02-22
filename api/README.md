# Analytics Backend (Mixpanel-lite)

## Architecture (Text Diagram)
`Client -> /v1 track|batch -> auth + limits + idempotency -> events_raw (partitioned)`
`events_raw insert -> job_queue enqueue (deduped key)`
`workers (SKIP LOCKED + backoff + lock TTL) -> minute/day rollups + sets`
`query APIs -> aggregates_* + *_set tables only`
`admin APIs -> tenant/key ops + maintenance jobs + audit logs`

## Key Enhancements
- Strong event dedupe with `(tenant_id, event_uuid)` unique index.
- Per-route request idempotency keying: `(tenant_id, route, idempotency_key)`.
- Tenant-level DB rate limit (`tenants.rate_limit_per_min`) with UPSERT counters.
- Job queue dedupe (`tenant_id, job_type, job_key`) for active jobs.
- Lock TTL requeue + exponential backoff + dead-letter status.
- Rollups: minute/hour/day event counts and hourly/daily revenue.
- Event registry (`events_registry`) + strict schema mode (`tenants.strict_schema`).
- System status endpoint with queue and partition health.

## Commands
- Test: `mvn -f analytics/pom.xml test`
- Run locally: `mvn -f analytics/pom.xml spring-boot:run`
- Run docker: `cd analytics && docker compose up --build`

## Endpoints
### Ingestion
- `POST /v1/track`
- `POST /v1/batch`

### Queries
- `GET /v1/metrics/event-counts?from=&to=&interval=minute|hour|day&event=`
- `GET /v1/metrics/dau?from=&to=`
- `GET /v1/metrics/revenue?from=&to=&interval=hour|day&currency=`
- `GET /v1/funnels/{funnelId}?from=&to=`
- `GET /v1/retention/{retentionId}?cohortFrom=&cohortTo=`
- `GET /v1/events?limit=&offset=`
- `GET /v1/events/{eventName}`
- `GET /v1/system/status`

### Admin
- `POST /admin/tenants`
- `PATCH /admin/tenants/{id}`
- `POST /admin/tenants/{id}/keys`
- `POST /admin/tenants/{id}/keys/{keyId}/revoke`
- `GET /admin/tenants/{id}/keys`
- `POST /admin/jobs/run`
- `GET /admin/audit-logs?limit=&offset=`

## Example cURL
### Create tenant + key
```bash
curl -X POST http://localhost:9100/admin/tenants \
  -H "X-Admin-Key: super-admin-key" \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme"}'

curl -X POST http://localhost:9100/admin/tenants/<TENANT_ID>/keys \
  -H "X-Admin-Key: super-admin-key" \
  -H "Content-Type: application/json" \
  -d '{"name":"primary"}'
```

### Ingest batch with idempotency + dedupe
```bash
curl -X POST http://localhost:9100/v1/batch \
  -H "X-API-Key: <TENANT_API_KEY>" \
  -H "Idempotency-Key: batch-20260222-1" \
  -H "Content-Type: application/json" \
  -d '{
    "events":[
      {"event":"OrderPlaced","eventId":"11111111-1111-1111-1111-111111111111","userId":"u1","timestamp":"2026-02-22T10:20:30Z","schemaVersion":1,"properties":{"amount":"100.00","currency":"USD"},"context":{}},
      {"event":"OrderPlaced","eventId":"11111111-1111-1111-1111-111111111111","userId":"u1","timestamp":"2026-02-22T10:20:31Z","schemaVersion":1,"properties":{"amount":"100.00","currency":"USD"},"context":{}}
    ]
  }'
```

### Query event counts
```bash
curl "http://localhost:9100/v1/metrics/event-counts?from=2026-02-22T00:00:00Z&to=2026-02-23T00:00:00Z&interval=hour&event=OrderPlaced" \
  -H "X-API-Key: <TENANT_API_KEY>"

curl "http://localhost:9100/v1/metrics/event-counts?from=2026-02-01T00:00:00Z&to=2026-02-22T23:59:59Z&interval=day&event=OrderPlaced" \
  -H "X-API-Key: <TENANT_API_KEY>"
```

### Query DAU
```bash
curl "http://localhost:9100/v1/metrics/dau?from=2026-02-01&to=2026-02-22" \
  -H "X-API-Key: <TENANT_API_KEY>"
```

### Query system status
```bash
curl "http://localhost:9100/v1/system/status" \
  -H "X-API-Key: <TENANT_API_KEY>"
```

## Ops and Maintenance
- Partition creation ahead and retention cleanup run via worker system jobs.
- Manual kick:
```bash
curl -X POST http://localhost:9100/admin/jobs/run \
  -H "X-Admin-Key: super-admin-key" \
  -H "Content-Type: application/json" \
  -d '{"jobType":"PARTITION_MAINT"}'
```

## Retention + Partitioning Strategy
- `events_raw` is range-partitioned daily by `received_at`.
- App precreates `PARTITION_PRECREATE_DAYS` partitions.
- Cleanup drops old partitions older than `RETENTION_RAW_DAYS` and compacts aggregates.
