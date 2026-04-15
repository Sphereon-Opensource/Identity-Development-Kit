# Module lib-oauth2-server-resource-impl

Runtime for the Resource Server contracts declared in `lib-oauth2-server-resource-public`. It validates access tokens, introspects tokens against the AS, verifies DPoP proofs, and keeps the token and DPoP-nonce caches that the hot path relies on (in-memory by default; swap in durable caches when the deployment needs them).