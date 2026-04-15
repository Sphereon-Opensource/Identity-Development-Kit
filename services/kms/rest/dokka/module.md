# Module services-kms-rest

Deployable Ktor server that exposes the IDK KMS over HTTP. It is the server-side of the remote-KMS split: applications running on constrained platforms (or that want to centralise key custody) run the REST KMS provider client (`lib-crypto-kms-provider-rest`) against this service, while the actual cryptographic operations stay behind the service boundary.

Operationally this is the component whose deployment footprint matters: key-material custody, audit surface, and network exposure are concentrated here, so treat it as a first-class service in deployment topology, not a library.
