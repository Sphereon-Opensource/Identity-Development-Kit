# Module lib-idv-public

Public contract for Identity Verification (IDV): the command surface that describes an IDV definition, runs a driver-agnostic verification flow, and evaluates predicates over the returned attributes. It deliberately does not pick a verification mechanism; that is the job of a driver module such as `lib-idv-oidc` (OIDC IdP) or `lib-idv-wallet` (OID4VP wallet).

Depend on this module from code that has to declare or invoke IDV without caring which driver is active.
