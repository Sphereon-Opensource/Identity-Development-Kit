# Module lib-crypto-core-impl

Runtime for the COSE and JOSE contracts declared in `lib-crypto-core-public`. It implements COSE signing and verification, key encoding, and the CBOR codecs COSE structures need, and adapts those operations through the IDK command system so higher layers (mdoc, SD-JWT, OAuth2/OIDC) share a single crypto surface.

A KMS provider module (software, AWS, Azure, mobile, or REST) still has to be on the classpath to supply the underlying signing backend.
