# Module lib-credential-claims-mapper-impl

Runtime for the claims-mapping surface declared in `lib-credential-claims-mapper-public`. It projects claims out of credentials (SD-JWT today, with room for other formats) onto target attribute sets such as DCQL outputs or OIDC id_token claims, driven by the mapping DSL configured elsewhere.

The default configuration store keeps mappings in memory; swap it out when you need durable configuration.
