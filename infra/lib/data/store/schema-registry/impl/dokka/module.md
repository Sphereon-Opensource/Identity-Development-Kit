# Module lib-data-store-schema-registry-impl

Runtime for the schema-registry contracts in `lib-data-store-schema-registry-public`. It wires the registry onto a `BlobStore` and the IDK command system so that versioned credential or data schemas can be fetched, published, and referenced through the same machinery as any other blob artefact.
