# Database schema

Lexicon stores events through a single internal JPA entity (`EventRecord`),
mapped with Hibernate ORM. That entity has no hardcoded table or column
names and no hardcoded SQL column types — every name and type comes from
Hibernate's own naming strategy and dialect. This means the physical schema
adapts to whatever convention fits your database, instead of Lexicon
imposing one convention on every database vendor.

## Default schema

If you don't configure a naming strategy, Hibernate uses the Java
identifiers as-is. With the current `EventRecord` entity, that produces:

| Element | Name |
|---|---|
| Table | `EventRecord` |
| Columns | `id`, `aggregateId`, `aggregateType`, `eventType`, `eventData`, `version`, `timestamp` |
| Unique index | `idx_event_streams` on `(aggregateId, version)` |

Column types (varchar lengths, the JSON payload's large-text type, `bigint`
for `version`, etc.) are picked by Hibernate's dialect for whichever
database you're connected to — Lexicon doesn't specify raw SQL types.

## Choosing a naming convention

Because names aren't hardcoded, you can pick a convention that matches your
database through the standard Hibernate/Quarkus configuration — Lexicon
doesn't need any configuration of its own.

**Postgres** conventionally uses snake_case. Configure Hibernate's built-in
`CamelCaseToUnderscoresNamingStrategy`:

```properties
quarkus.hibernate-orm.physical-naming-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
```

This turns the default schema above into table `event_record` with columns
`id`, `aggregate_id`, `aggregate_type`, `event_type`, `event_data`,
`version`, `timestamp`.

**SQL Server** more commonly matches the unmodified Java identifiers (or a
PascalCase variant), so leaving `physical-naming-strategy` unset — the
default shown above — is usually the better fit. If you want a different
convention, supply your own `PhysicalNamingStrategy`/`ImplicitNamingStrategy`
implementation and point Hibernate at it the same way.

## A note on migrations

Since the physical names depend on whichever naming strategy your
application configures, don't assume the default names above when writing
migrations or manual SQL (see [Event versioning](03-event-versioning.md)
for an example) — check your actual generated schema first, e.g. by
running with `quarkus.hibernate-orm.database.generation=drop-and-create` and
`quarkus.hibernate-orm.log.sql=true` against a throwaway database.
