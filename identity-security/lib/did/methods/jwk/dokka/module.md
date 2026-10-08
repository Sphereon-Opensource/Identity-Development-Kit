# Module lib-did-methods-jwk

`did:jwk` method provider and resolver. Adds support for DIDs whose document is derived directly from a JWK, which is a good fit for ephemeral issuer and holder keys that do not need any ledger or hosting infrastructure.

Adding this module to the classpath is enough to register the method with the DID manager and the resolver registry.
