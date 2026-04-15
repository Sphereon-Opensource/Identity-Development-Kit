# Module lib-identity-reconciliation-public

Public API for reconciling identity attributes across sources. It exposes the attribute-mapping and normalisation surface, the canonical-attribute rule model, and the OIDC connection resolver, so attributes collected from multiple providers can be reshaped into a canonical form for matching and resolution.

Depend on this module from code that has to produce canonical attributes or drive a reconciliation session. Runtime implementations live in `lib-identity-reconciliation-impl`.
