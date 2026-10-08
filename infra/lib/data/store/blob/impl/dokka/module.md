# Module lib-data-store-blob-impl

Common runtime shared by the concrete `BlobStore` backings. It supplies the content-addressable layer, the default blob-info resolver, and the blob-store command surface that every backing benefits from, so per-backing modules only have to provide the storage-specific pieces.

Any `impl-*` backing module pulls this in transitively.
