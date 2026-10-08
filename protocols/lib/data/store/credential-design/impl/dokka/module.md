# Module lib-data-store-credential-design-impl

Runtime for the credential-design surface in `lib-data-store-credential-design-public` (infra pack). Protocol-aware mappers (OID4VCI / SD-JWT VCT) live in the protocols pack so infra does not depend on protocol artifacts. It stores render metadata, localisation, and branding for issued credentials, typically on top of a `BlobStore` so design artefacts live alongside the other per-tenant assets.
