# Module lib-openid-oid4vp-verifier-impl

Runtime for the OID4VP verifier contracts declared in `lib-openid-oid4vp-verifier-public`. It builds unsigned and signed authorization requests, produces the authorization-request URIs wallets consume, and drives the request-object signing configuration from the verifier's declared policy.

This module is the per-verifier runtime; the cross-session coordination surface that a deployable service works against lives in `lib-openid-oid4vp-universal-*`, and the HTTP-exposed server lives in `services-oid4vp-verifier-rest`.
