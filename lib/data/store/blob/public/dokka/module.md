# Module lib-data-store-blob-public

Public blob / object-storage contracts. It defines the descriptor, metadata, info-resolver, and event model that higher-level modules (credential design, schema registry, tenant asset storage) use to treat blob content uniformly, independent of where the bytes actually live.

Depend on this module from library code that handles blobs without committing to a backend. A concrete backing, such as `impl-memory`, `impl-fs`, `impl-kv`, `impl-okd`, or `client-http`, still has to be on the classpath for actual storage.
