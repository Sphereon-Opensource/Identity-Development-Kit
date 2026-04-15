# Module lib-trust-did

DID-based trust validator plugged into the trust-core framework (`lib-trust-core-public`). It treats DIDs as the trust anchors, resolving the DID document (via IDK's DID resolver stack) and deriving entity info and validation decisions from the verification methods and service endpoints it declares.

Reach for this module when counterparties are identified and trusted by DID, for example in decentralised issuer/verifier topologies. Pair with the DID resolver and the method modules for the methods you want supported (`lib-did-methods-key`, `-jwk`, `-web`).
