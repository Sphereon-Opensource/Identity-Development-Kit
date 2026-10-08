# Module lib-did-manager-public

Public DID lifecycle API: the manager surface that owns creation, update, and deactivation of DIDs, the provider contract each DID method implements, and the creation DSL used by higher-level flows (for example when an OID4VCI issuer needs a new signing DID for a credential). Method-agnostic on purpose, so downstream code stays decoupled from which methods are actually registered.

Depend on this module from any code that has to manage, not just resolve, DIDs. Runtime wiring lives in `lib-did-manager-impl`.
