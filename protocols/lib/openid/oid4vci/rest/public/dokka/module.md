# Module lib-openid-oid4vci-rest-public

REST-facing service contract for OID4VCI issuers. It adds the operational concerns that the core protocol surface deliberately does not cover: session management for credential offers, a deployable configuration model, typed events for observability, and the session-store abstraction so sessions can live in any `KvStore`-backed substrate.

Depend on this module from code that has to embed or wrap the REST issuer service layer. Runtime implementations live in `lib-openid-oid4vci-rest-impl`.
