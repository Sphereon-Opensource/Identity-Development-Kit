# Module services-kms-rest

Ktor hosting support for a KMS service. `configureKms` installs the per-request dependency-injection plumbing that resolves a tenant and principal from the request, plus JSON negotiation and a liveness route.

It mounts no KMS endpoints of its own. Key material is reachable only through the typed resource surface, addressed by opaque resource handles, which the hosting assembly contributes as its own HTTP adapters. Final KMS leaf commands are broker-only and carry no public HTTP route: there is no generic key, provider, or resolver catalog, and nothing addresses key material by raw alias, kid, or provider id.

Operationally this is the component whose deployment footprint matters: key-material custody, audit surface, and network exposure are concentrated here, so treat it as a first-class service in deployment topology, not a library.
