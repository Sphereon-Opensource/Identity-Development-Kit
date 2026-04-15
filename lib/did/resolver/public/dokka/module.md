# Module lib-did-resolver-public

Public DID resolution API. It defines the resolver surface, the resolution and dereferencing types, the resolution cache contract, and the command bindings higher layers dispatch through when they need to resolve a DID without knowing which method is in play.

Depend on this module from any code that takes a DID and wants a DID document back. Runtime resolution lives in `lib-did-resolver-impl`, and method-specific resolvers live under `lib-did-methods-*`.
