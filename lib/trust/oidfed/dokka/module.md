# Module lib-trust-oidfed

OpenID Federation trust validator plugged into the trust-core framework. It walks entity statements, follows the trust chain up to configured anchors, and produces the validation decision the trust-core runtime exposes to callers.

Reach for this module in OID4VC deployments that use OpenID Federation as their trust-establishment mechanism (issuer and verifier metadata signed and chained to federation anchors). It is a network-active module: entity-statement resolution requires outbound HTTPS to the federation entities involved.
