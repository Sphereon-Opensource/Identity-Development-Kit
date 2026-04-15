# Module lib-crypto-core-public

Public crypto model shared by everything in IDK that signs, verifies, or encodes keys: the KMS provider family, the mdoc stack, SD-JWT, and the OAuth2/OIDC layer. It defines COSE header and key types, COSE MAC structures, algorithm identifiers, and the CBOR-codec surface COSE depends on.

This module is model-only. Runtime signing and codec behaviour lives in `lib-crypto-core-impl`, and the actual cryptographic operations are delegated to a KMS provider module.
