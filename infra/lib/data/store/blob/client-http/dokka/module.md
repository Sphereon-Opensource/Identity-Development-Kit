# Module lib-data-store-blob-client-http

HTTP client backing that makes a remote blob service look like a local `BlobStore` to the rest of the platform. Reach for this when an application keeps blob state in a central service but wants modules that consume blobs (credential design, schema registry, asset storage) to stay agnostic about where the bytes actually live.