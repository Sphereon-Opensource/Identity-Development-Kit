# Module lib-openid-oid4vp-dcql

Digital Credentials Query Language (DCQL) data model used by OID4VP verifiers to describe what they want presented and by holders to reason about what they can present. It covers the query shape, the response structure, per-format metadata (mdoc, SD-JWT VC, and so on), and the trusted-authority and error types the query layer needs.

Model-only: evaluating a DCQL query against the credentials a holder actually holds happens in the holder runtime and the claims-mapper layer, not here.
