# Module lib-openid-oid4vp-universal-impl

Runtime for the universal OID4VP service layer declared in `lib-openid-oid4vp-universal-public`. It manages verifier-side authorization-request sessions end to end (create, delete, status) and assembles the verified-data projection a deployed service hands back to its callers.

Used by `services-oid4vp-verifier-rest` as the in-process service surface.
