# OID4VC Examples

Examples for OpenID for Verifiable Credentials (OID4VCI issuance and OID4VP presentation).

## Folder Structure

| Folder | Description |
|--------|-------------|
| **[services/](services/)** | Docker Compose environment that runs all IDK OID4VC services (OAuth2 AS, OID4VCI Issuer, OID4VP Verifier) behind a Caddy reverse proxy. Use this to test wallet flows against a local or tunnel-exposed endpoint. See [services/README.md](services/README.md) for setup instructions. |
| **[webapp/](webapp/)** | Demo web application with a browser-based frontend and a Ktor backend. The frontend lets you walk through issuance and presentation flows without a native wallet. |
