# Database Migration Strategy

## Principles
- PostgreSQL schema is managed by Flyway only.
- Hibernate never mutates schema (`ddl-auto=validate`).
- Every database change is SQL-first and version-controlled.
- No direct manual edits in shared environments.

## Migration Rules
- Naming format: `V{number}__description.sql`
- One concern per migration where practical.
- Include schema objects only (tables, indexes, constraints, sequences, functions).
- Seed only essential system data in dedicated seed migrations.

## Creating a New Migration
1. Add a new file under `api/src/main/resources/db/migration`.
2. Use next version number with clear description.
3. Write forward-only SQL.
4. Validate locally on an empty database.
5. Commit migration with related app code in the same change.

## Local Migration Run
1. Ensure `.env` has valid DB credentials.
2. Start PostgreSQL.
3. Run app:
   - `mvn -f api/pom.xml spring-boot:run`
4. Flyway runs automatically before JPA validation.

## Rollback Strategy
- Flyway Community is forward-only; rollback is operational:
1. Restore from last verified Postgres backup.
2. Re-deploy matching application version.
3. Re-apply safe migrations if needed.

## Handling Failed Migration
1. Stop deployment pipeline for that environment.
2. Inspect `flyway_schema_history` and DB error details.
3. If failure is non-transactional side effect, restore backup.
4. Add corrective migration (`V{next}__...sql`), do not edit applied migration files.
5. Re-run deployment and confirm checksum validation passes.

## Guardrails
- `spring.flyway.validate-on-migrate=true`
- `spring.flyway.baseline-on-migrate=false`
- `spring.jpa.hibernate.ddl-auto=validate`
- Any checksum mismatch or missing migration must fail startup.
