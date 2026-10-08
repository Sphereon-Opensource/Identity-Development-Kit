# Module lib-data-store-blob-impl-kv

`BlobStore` implementation layered on top of any `KvStore` from `lib-data-store-kv-public`. Useful when you have already committed to a particular key-value backing (memory, Kottage, etc.) and want blobs to live in the same substrate without standing up a second storage system.
