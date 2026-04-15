# Module lib-identity-matching-public

Public API for privacy-preserving identity matching. It defines the hashed-identifier and encrypted-payload primitives, the reconciliation-oriented crypto surface, and the command bindings that let one source match an identity against another without disclosing raw identifiers.

Depend on this module from any code that has to participate in a cross-source match. Runtime implementations live in `lib-identity-matching-impl`; higher-level resolution flows live in `lib-identity-resolution-*`.
