# Module lib-trust-core-impl

Runtime for the trust-core contracts in `lib-trust-core-public`. It drives entity discovery, trust-anchor refresh, and revocation checking, dispatching to whichever source-specific validators (`lib-trust-etsi`, `lib-trust-x509`, `lib-trust-did`, `lib-trust-oidfed`) happen to be on the classpath.

Depend on this alongside at least one source-specific module; on its own it has no trust sources to consult.
