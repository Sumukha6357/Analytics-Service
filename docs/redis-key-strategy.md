# Redis Key Strategy

## Goals
- Prevent collisions across tenants and modules.
- Keep keys discoverable and easy to expire.
- Allow safe schema evolution through versioned prefixes.

## Naming Rules
- Use lowercase namespaces separated by `:`.
- Include tenant scope for multi-tenant data.
- Version cache keys with `v{n}` when payload shape changes.
- Keep identifiers explicit (`bookingId`, `userId`, `deviceId`).

## Standard Patterns
- Booking lock: `booking:lock:{tenantId}:{bookingId}`
- Request idempotency: `idempotency:{tenantId}:{key}`
- Session: `session:{tenantId}:{userId}:{deviceId}`
- Entity cache (versioned): `cache:v1:{tenantId}:{entity}:{id}`
- Rate limiter bucket: `ratelimit:{tenantId}:{route}:{minuteEpoch}`

## TTL Guidance
- Locks: short TTL (15s to 120s).
- Idempotency: medium TTL (5m to 24h by endpoint criticality).
- Sessions: product policy based (for example 24h to 30d).
- Cached read models: short TTL (15s to 300s) unless immutable.

## Versioning Policy
- Bump cache namespace version when serialized shape changes.
- Do not reuse retired versions.
- Expire old versions naturally with TTL; avoid blocking deletes.

## Operational Notes
- Avoid storing unbounded collections in a single key.
- Use `SCAN` only for maintenance/debug, not hot-path logic.
- Track key count and memory usage in observability dashboards.
