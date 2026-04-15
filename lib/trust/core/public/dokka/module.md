# Module lib-trust-core-public

Core trust framework contract. It defines the cross-cutting surface every trust source in IDK implements against: entity discovery, trust-anchor retrieval and refresh, and revocation checking. The point of this module is to give the rest of the platform a single trust abstraction that does not care whether the underlying trust mechanism is ETSI LOTL, X.509, DID, or OpenID Federation.

Depend on this module from code that consumes trust decisions (for example a verifier or issuer that has to assess a counterparty). Runtime plumbing lives in `lib-trust-core-impl`; the source-specific validators live in `lib-trust-etsi`, `lib-trust-x509`, `lib-trust-did`, and `lib-trust-oidfed`.
