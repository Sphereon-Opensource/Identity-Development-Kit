# Module lib-did-persistence-api

Public contract for storing managed DID records (identifiers, key references, lifecycle state). It is the surface the DID manager reads and writes against; concrete storage (in-memory or SQLite) lives in sibling modules.

Depend on this module from code that has to look up or persist DID records. Pair with `lib-did-persistence-memory` for tests or ephemeral deployments, or `lib-did-persistence-sqlite` for durable storage.
