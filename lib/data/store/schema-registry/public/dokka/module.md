# Module lib-data-store-schema-registry-public

Public schema-registry API for versioned credential and data schemas. It gives issuer and verifier code a stable contract to resolve, publish, and reference schema documents without knowing that the storage is a blob store underneath.

Depend on this module from any code that has to name or look up a schema version. Runtime implementations live in `lib-data-store-schema-registry-impl`.
