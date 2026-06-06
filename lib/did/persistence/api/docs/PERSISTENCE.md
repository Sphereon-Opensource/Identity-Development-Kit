# DID Manager persistence configuration

The DID manager reads its `DidRepository` from a config-driven selector contributed by
`lib-did-manager-impl`. Each persistence dialect contributes a `DidRepositoryFactory` into a
Metro multimap keyed by [`DidPersistenceConfig.type`](src/commonMain/kotlin/com/sphereon/did/persistence/DidPersistenceConfig.kt);
the selector reads `did.persistence.*` properties at app-graph init and picks the matching
factory.

## Properties

All keys are optional. Missing `did.persistence.type` defaults to `memory`.

| Key | Type | Default | Notes |
| --- | --- | --- | --- |
| `did.persistence.type` | string | `memory` | Dialect discriminator. One of the values registered by the dialect modules on the classpath. |
| `did.persistence.connectionUrl` | string | none | JDBC URL or file path. Dialect-specific. |
| `did.persistence.username` | string | none | JDBC user. SQL dialects only. |
| `did.persistence.password` | string | none | JDBC password. SQL dialects only. |
| `did.persistence.poolSize` | integer | dialect-specific | JDBC connection pool size. |
| `did.persistence.properties.<key>` | string | none | Free-form per-dialect overrides. |

## Dialects

| `type` | Module | Where it lives |
| --- | --- | --- |
| `memory` | `lib-did-persistence-memory` | IDK |
| `sqlite` | `lib-did-persistence-sqlite` | IDK (JVM-only) |
| `postgresql` | `lib-did-persistence-postgresql` | EDK |
| `mysql` | `lib-did-persistence-mysql` | EDK |

To enable a dialect, add the corresponding module to the consuming application's runtime
classpath. The selector fails fast at graph init if `did.persistence.type` references a
dialect whose factory is not on the classpath.

## Examples

### Memory (default)

```properties
# Nothing required — memory is the default.
# Ephemeral, lost on restart. Good for dev, smoke tests, and any service that
# only resolves DIDs (not authoritative for the manager surface).
```

### SQLite — file

```properties
did.persistence.type=sqlite
did.persistence.connectionUrl=/var/lib/idk/dids.db
```

The SQLite factory accepts either a bare file path or a full `jdbc:sqlite:…` URL. When
no `connectionUrl` is supplied the factory uses `jdbc:sqlite::memory:` (per-process,
non-shared, ephemeral — useful for local experiments).

### SQLite — shared in-memory (per-test fixture)

```properties
did.persistence.type=sqlite
did.persistence.connectionUrl=jdbc:sqlite:file:dids?mode=memory&cache=shared
```

### PostgreSQL (EDK module required)

```properties
did.persistence.type=postgresql
did.persistence.connectionUrl=jdbc:postgresql://localhost:5432/idk
did.persistence.username=idk
did.persistence.password=idk
did.persistence.poolSize=10
```

### MySQL (EDK module required)

```properties
did.persistence.type=mysql
did.persistence.connectionUrl=jdbc:mysql://localhost:3306/idk?useSSL=false
did.persistence.username=idk
did.persistence.password=idk
did.persistence.poolSize=10
```

## Custom dialects

A third-party persistence backend can plug in by:

1. Implementing `DidRepositoryFactory` (set `type` to a unique string).
2. Contributing it via Metro:

   ```kotlin
   @ContributesTo(AppScope::class)
   interface MyDialectModule {
       @Provides @IntoMap @StringKey("my-backend") @SingleIn(AppScope::class)
       fun provide(): DidRepositoryFactory = MyFactory()
   }
   ```

3. Setting `did.persistence.type=my-backend` in application config.
