# Module lib-openid-oid4vci-common-public

Shared OID4VCI model and DSLs used by both issuer and holder sides. Data classes mirror the OID4VCI spec wire format (credential configurations, value objects, and so on), and the DSLs let issuer and holder code compose command pipelines, metadata, and issuer-authenticated-encryption (IAE) payloads without per-side duplication.

Runtime helpers live in `lib-openid-oid4vci-common-impl`. The issuer and holder sides live in their own modules.
