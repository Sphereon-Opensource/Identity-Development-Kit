# Module lib-did-methods-key

`did:key` method provider and resolver. Covers DIDs whose identifier is a self-describing encoding of a public key, which is the simplest option for keys that do not need to be discovered externally and ships with the curve-decoding utilities needed for the supported key types.

Adding this module to the classpath registers both the provider and the resolver with the DID manager and resolver registries.
