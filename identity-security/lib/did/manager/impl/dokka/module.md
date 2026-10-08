# Module lib-did-manager-impl

Runtime for the DID manager contracts in `lib-did-manager-public`. It holds the provider registry, routes create, update, and deactivate calls to the right method provider, and drives the DID creation DSL through to the method layer. Method providers (`lib-did-methods-key`, `lib-did-methods-jwk`, `lib-did-methods-web`, etc.) plug in via the registry rather than being hard-coded here.

Pair with at least one method provider module to actually manage DIDs.
