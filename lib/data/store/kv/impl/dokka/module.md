# Module lib-data-store-kv-impl

Shared runtime scaffolding for concrete `KvStore` backings. It supplies the delegation, serialization, and namespacing helpers every backing needs, so `impl-memory`, `impl-kottage`, and similar modules only have to implement the storage-specific pieces.

Pulled in transitively by any concrete backing.
