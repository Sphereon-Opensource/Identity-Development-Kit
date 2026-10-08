# Module lib-did-resolver-impl

Runtime for the DID resolution contracts declared in `lib-did-resolver-public`. This is what you depend on when you actually need to resolve and dereference DIDs, rather than just describe them: the registry, the resolution cache, the external-identifier resolution service, and the command bodies that higher layers call into.

Method-specific resolvers (`lib-did-methods-key`, `lib-did-methods-jwk`, `lib-did-methods-web`, others) plug into the registry, so registering a new DID method is a matter of putting its module on the classpath. Nothing in this module has to change.
