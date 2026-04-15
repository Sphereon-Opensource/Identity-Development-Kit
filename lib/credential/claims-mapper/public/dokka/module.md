# Module lib-credential-claims-mapper-public

Public contract for projecting credential claims onto a target attribute schema. The mapping DSL lets an issuer or verifier configure, declaratively, how claims from a credential (for example an SD-JWT VC) translate into the attribute shape a consumer expects (for example a DCQL response or an OIDC id_token). The module also exposes the DCQL adapter surface used by the OID4VP layer.

Depend on this module when you need to describe or invoke a mapping from library code without committing to a particular credential format or configuration backend. Runtime mapping lives in `lib-credential-claims-mapper-impl`.
