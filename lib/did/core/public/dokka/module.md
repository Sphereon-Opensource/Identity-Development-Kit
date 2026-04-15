# Module lib-did-core-public

Shared capability model that every IDK DID method provider implements against. It describes what a method supports (key management, lifecycle operations, representation) and the metadata the DID manager and resolver need to pick the right provider per operation. Model-only; the method providers themselves live under `lib-did-methods-*`, and the manager and resolver runtimes live under `lib-did-manager-*` and `lib-did-resolver-*`.
