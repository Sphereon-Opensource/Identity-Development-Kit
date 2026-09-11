# IDK Services Demo Environment

Local development setup for testing OID4VCI credential issuance and OID4VP credential presentation with external wallets.

## Services

| Service | Container Port | Description |
|---------|---------------|-------------|
| **Caddy** | 8080 (exposed) | Reverse proxy. Single entry point for all services |
| **OAuth2 AS** | 8080 | Authorization Server with built-in test login |
| **OID4VCI Issuer** | 8080 | Credential issuer with SD-JWT (`TestCredential`, `EuPid`) and mdoc (`AgeOver18`) |
| **OID4VP Verifier** | 8080 | Verifier for credential presentations |

All services are accessed through Caddy on a single port (default `8080`). Caddy routes by path:

- `/oid4vci/*` → Issuer (identifier: `${EXTERNAL_BASE_URL}/oid4vci`)
- `/oid4vp/*` → Verifier
- `/auth/*` → OAuth2 AS (issuer: `${EXTERNAL_BASE_URL}/auth`)

Well-known metadata discovery:

| Endpoint | Spec | Location |
|----------|------|----------|
| Issuer metadata | OID4VCI 11.2.2 / RFC 8615 | `/.well-known/openid-credential-issuer/oid4vci` (also `/oid4vci/.well-known/openid-credential-issuer` for pre-1.0-final wallets) |
| AS metadata | RFC 8414 Section 3 | `/.well-known/oauth-authorization-server/auth` (also at root `/.well-known/oauth-authorization-server`) |
| OIDC discovery | RFC 8414 Section 5 | `/.well-known/openid-configuration/auth` (also at root `/.well-known/openid-configuration`) |

## Quick Start

Two entrypoints are provided. Pick the one that matches your situation:

| Script | Purpose | Requires IDK source? | Builds images? | Pulls from Docker Hub? |
|---|---|---|---|---|
| `start.sh` / `start.bat` | Run published `sphereon/idk-*` images | no | no | yes (`docker compose pull`) |
| `start-dev.sh` / `start-dev.bat` | Iterate on local IDK source | yes | yes (via `docker compose up --build`) | no |
| `build-images.sh` / `.bat` | Produce release-candidate images locally | yes | yes (tagged version + latest + git sha) | no |
| `publish-images.sh` / `.bat` | Push release images to a Docker registry | yes | pushes pre-built tags | push |

**End-users / demos (published images):**

```bash
./start.sh                                   # Linux / macOS, auto-detect LAN IP, default profile
./start.sh https://my.ngrok.app              # Pass external URL
./start.sh https://my.ngrok.app haip         # Layer the HAIP conformance profile
IDK_VERSION=0.25.0 ./start.sh                # Pin a specific release
EXTERNAL_BASE_URL=http://192.168.1.100:8080 ./start.sh
```
```cmd
start.bat                                    REM Windows
start.bat https://my.ngrok.app
start.bat https://my.ngrok.app haip          REM HAIP conformance profile
set IDK_VERSION=0.25.0 && start.bat
```

**Contributors iterating on IDK source (local build):**

```bash
./start-dev.sh
./start-dev.sh https://my.ngrok.app
./start-dev.sh https://my.ngrok.app haip     # HAIP conformance profile
```
```cmd
start-dev.bat
start-dev.bat https://my.ngrok.app haip
```

### Conformance profiles (second positional argument)

The OIDF conformance suite tests three OID4VP `client_id` prefix modes
plus a HAIP-shaped OAuth2 AS. The start scripts accept a profile name as
the second positional argument and translate it into the right
`docker compose --env-file` layering:

| Arg | Verifier prefix | AS shape | When to use |
|---|---|---|---|
| _omitted_ / `default` | `did:jwk` | plain | Regular demo. |
| `did-jwk` | `did:jwk` (explicit) | plain | OID4VP plan with `did:jwk` cells; identical to default. |
| `x509-san-dns` | `x509_san_dns` | plain | OID4VP plan with `x509_san_dns` cells. Requires a fresh keystore baked with the SAN — delete `keystores/oid4vp-verifier/default/keystore.p12` and rerun if you've used a previous keystore that didn't have the SAN. |
| `x509-hash` | `x509_hash` | plain | OID4VP plan with `x509_hash` cells. The keystore script auto-syncs the cert thumbprint into `profiles/conformance-x509-hash.env`. |
| `haip` | `x509_hash` | HAIP-shaped | OID4VP HAIP test plan + OID4VCI HAIP test plan. Layers the verifier x509_hash profile and a HAIP-shaped AS profile that switches `token_endpoint_auth_methods_supported` to `attest_jwt_client_auth` (the IANA-registered name for OAuth Attestation-based Client Authentication), sets `dpop=REQUIRED` + `dpop_nonce_required=true`, and turns attestation-based client auth on. |

Bogus values fail fast with a usage message listing the allowed set.
The shipped `.env` (`EXTERNAL_BASE_URL`, `IDK_VERSION`) is always
loaded automatically; profile env files layer on top.

For full conformance-testing setup including trust-anchor configuration
and test-plan to profile mapping, see
[`docs/conformance/running-the-suite.md`](../../../docs/conformance/running-the-suite.md).

### Versioning

Docker image tags track IDK's own version from `vdx/edk/idk/gradle.properties` (`version=...`). No manual version string duplication. A release of IDK becomes an image release of the four services `sphereon/idk-oauth2-as`, `sphereon/idk-oid4vci-issuer`, `sphereon/idk-oid4vp-verifier`, `sphereon/idk-oid4vc-webapp`.

Version resolution in `start.sh`:
1. `IDK_VERSION` env var (highest priority)
2. `vdx/edk/idk/gradle.properties` `version=` field, if the IDK source is checked out
3. Fallback: `latest`

### Publishing images

Operators push new release images like this:

```bash
docker login docker.io

# Release build (refuses if tree is dirty or version is *-SNAPSHOT)
./publish-images.sh

# Preview push of a SNAPSHOT build
./publish-images.sh --allow-snapshot

# Override the registry (default: sphereon on docker.io)
REGISTRY=ghcr.io/sphereon ./publish-images.sh
```

`:latest` is only pushed for non-SNAPSHOT releases to keep that tag safe. Per-version tags and the git-SHA tag are always pushed.

Verify:
```bash
curl http://<your-ip>:8080/auth/health
curl http://<your-ip>:8080/.well-known/openid-credential-issuer/oid4vci
curl http://<your-ip>:8080/.well-known/oauth-authorization-server/auth
```

Stop:
```bash
docker compose down
```

## Configuration

### External URL (`EXTERNAL_BASE_URL`)

All wallet-facing URLs (metadata, QR codes, credential offer URIs, VCT type URLs) use this value. Must be reachable from the wallet device.

| Method | Example |
|--------|---------|
| Auto-detect | `./start.sh` (detects LAN IP) |
| Argument | `./start.sh https://my.ngrok.app` |
| `.env` file | `EXTERNAL_BASE_URL=http://192.168.1.100:8080` |
| Environment | `EXTERNAL_BASE_URL=https://my.ngrok.io ./start.sh` |
| Port change | Set in both `.env` and `docker-compose.yaml` ports mapping |

### Test User Credentials

The OAuth2 AS has a built-in test login at `/login`:

| Field | Value |
|-------|-------|
| Username | `testuser` |
| Password | `testpass` |
| Email | test@example.com |
| Name | Test User |

### Credential Configuration

The issuer ships three pre-configured credential types (see `config/oid4vci-issuer.yml`, `credential-configuration-ids: TestCredential,EuPid,AgeOver18`):

| Configuration ID | Format | VCT / Doctype | Signing Key Alias | Purpose |
|---|---|---|---|---|
| `TestCredential` | `dc+sd-jwt` | `${EXTERNAL_BASE_URL}/public/schema/vct/TestCredential` | `TestCredential` | Simple demo SD-JWT with `given_name`, `family_name`, `email` |
| `EuPid` | `dc+sd-jwt` | `${EXTERNAL_BASE_URL}/public/schema/vct/EuPid` | `PID` | EU Personal ID (EUDI ARF) with ~14 claims |
| `AgeOver18` | `mso_mdoc` | doctype `eu.europa.ec.av.1` | `AgeOver18` | ISO 18013-5 mdoc age attestation |

To modify: edit `config/oid4vci-issuer.yml`.

#### VCT Type Metadata

Each SD-JWT credential type has a VCT (Verifiable Credential Type) URL that points to a type metadata document describing the credential's claims, display properties, and rendering. Per the SD-JWT VC spec (draft-ietf-oauth-sd-jwt-vc), wallets resolve this URL to get credential display information.

In this demo the issuer serves VCT metadata dynamically at `GET /public/schema/vct/{type}`, derived from the same `oid4vci.issuer` credential configuration that drives the OID4VCI credential-issuer metadata. A single config block (per-locale credential `display` and per-claim `display`) is the one authoring source for both surfaces, so there are no static VCT files to keep in sync. The issuer builds the type metadata through the shared, source-agnostic `buildSdJwtVcTypeMetadata(...)` function via the optional `VctTypeMetadataProvider` SPI; an EDK/VDX semantic catalog can contribute the same input later. mso_mdoc types have no `vct` and so return 404.

Included display locales per type (top-level `display` and per-claim `display`):

`en-US`, `de-DE`, `es-ES`, `nl-NL`, `fr-FR`, `zh-CN`, `ja-JP`

In production, VCT metadata is hosted through the blob store service.

#### Adding a credential type

To add a new credential type:

1. Create a VCT metadata file `vct/MyNewCredential.json` (see `vct/TestCredential.json` as template). Include the display locales you want wallets to render. Wallets pick the best match against the user's preferred locale.
2. Add the credential config in `config/oid4vci-issuer.yml`. Real-world snippet from the shipped config (the `"[Id]"` bracket form is how map keys are declared):

```yaml
oid4vci:
  issuer:
    credential-configuration-ids: TestCredential,EuPid,AgeOver18,MyNewCredential
    credentials:
      "[MyNewCredential]":
        format: "dc+sd-jwt"
        vct: "${env:EXTERNAL_BASE_URL}/public/schema/vct/MyNewCredential"
        signing-key-alias: MyNewCredential
```

3. If you need a dedicated signing key, add its alias to `generate_keystore "oid4vci-issuer" ...` in `lib/generate-keystores.sh` (and the `.ps1` equivalent).

### OAuth2 Authorization Server

Configuration in `config/oauth2-as.yml`. Live values from the shipped config:

| Property | Default | Description |
|----------|---------|-------------|
| `oauth2.servers.default.mode` | `HOSTED` | AS mode (`HOSTED` = embedded) |
| `oauth2.servers.default.oidc` | `SUPPORTED` | OIDC support (`DISABLED`, `SUPPORTED`, `REQUIRED`) |
| `oauth2.servers.default.pkce` | `REQUIRED` | PKCE policy (`DISABLED`, `SUPPORTED`, `REQUIRED`) |
| `oauth2.servers.default.token-format` | `JWT` | Token format (`JWT`, `OPAQUE`) |
| `oauth2.servers.default.access-token-lifetime-seconds` | `3600` | Access token TTL |
| `oauth2.servers.default.authorization-code-lifetime-seconds` | `600` | Auth code TTL |
| `oauth2.servers.default.grant-types-enabled` | `authorization_code`, `urn:ietf:params:oauth:grant-type:pre-authorized_code`, `client_credentials`, `refresh_token` | Enabled grant types |
| `oauth2.servers.default.public-clients.allow-any` | `true` | Accept any public client (demo only) |
| `oauth2.servers.default.internal-clients.issuer.client-id` | `issuer-service` | Service client used by the issuer to call the AS |

Additional grant type that can be enabled:
- `urn:ietf:params:oauth:grant-type:token-exchange`. Token exchange (RFC 8693)

### OID4VP Verifier

Configuration in `config/oid4vp-verifier.yml`. The verifier's DCQL query is specified per-request via the REST API, not in static config.

### Service-Level Overrides

Any property can be overridden via environment variables in `docker-compose.yaml`. The naming convention is:

```
YAML path: oid4vci.issuer.identifier
Env var:   OID4VCI_ISSUER_IDENTIFIER
```

Dots become underscores, keys are uppercased. Config keys use bare domain prefixes (no `sphereon.` prefix).

## Supported Flows

### Pre-Authorized Code Flow

No user login required. The issuer creates a credential offer with a pre-authorized code.

1. **Create offer**. `POST /api/oid4vci/v1/backend/credential/offers`
2. Wallet scans QR code from the response
3. Wallet exchanges pre-auth code at `POST /auth/token`
4. Wallet requests credential at `POST /oid4vci/credential`

### Authorization Code Flow

Requires user login. The wallet redirects to the AS for authentication.

1. **Create offer**. `POST /api/oid4vci/v1/backend/credential/offers` with `authorization_code` grant
2. Wallet scans QR, opens authorization URL in browser
3. User logs in at `/auth/login` (testuser/testpass)
4. AS redirects back with authorization code
5. Wallet exchanges code at `POST /auth/token`
6. Wallet requests credential at `POST /oid4vci/credential`

### Credential Presentation (OID4VP)

1. **Create auth request**. `POST /oid4vp/backend/auth/requests` with DCQL query
2. Wallet scans QR code
3. Wallet fetches request object from `/oid4vp/request-uri/{id}`
4. Wallet submits VP to the direct_post endpoint
5. **Check result**. `GET /oid4vp/backend/auth/requests/{id}`

## Postman Collection

Import `postman/IDK-OID4VCI-OID4VP-E2E.postman_collection.json` into Postman.

Set the `base_url` collection variable to your `EXTERNAL_BASE_URL`.

The collection has five folders and 66 requests:

- **Setup**. Health checks, metadata discovery
- **Pre-Authorized Code Flow**. Full issuance flow (auto-extracts tokens between requests)
- **Authorization Code Flow**. Issuance with user login (some manual steps)
- **OID4VP Verification**. Create request, check result
- **Authorization Server Administration**. The same 50-request UUID-resource,
  lifecycle, discovery, client/identity, federation-binding, OID4VCI selection,
  protocol-profile, and migration-remediation contract segment maintained in
  the customer collection. Scenarios requiring stale discovery or failed
  migration rows need the seeded release-gate database state.

Automated smoke checks against a running compose environment:

```bash
npx newman run postman/IDK-OID4VCI-OID4VP-E2E.postman_collection.json --folder Setup
npx newman run postman/IDK-OID4VCI-OID4VP-E2E.postman_collection.json --folder "Pre-Authorized Code Flow"
```

The pre-authorized flow creates an offer, fetches the `credential_offer_uri`, exchanges the
pre-authorized code at `/auth/token`, and requests a nonce. The final credential request still uses
`<INSERT_JWT_PROOF_HERE>` and returns 400 until a wallet-generated proof bound to the nonce is
provided.

## Troubleshooting

**Wallet can't reach services:**
- Ensure `EXTERNAL_BASE_URL` uses your LAN IP, not `localhost`
- Check firewall allows port 8080
- Verify with `curl` from another device on the same network

**Services won't start:**
- Check Docker is running: `docker info`
- Check build logs: `docker compose logs oauth2-as`
- First build takes time (downloads dependencies, compiles)

**Pre-auth code expired:**
- Default TTL is 10 minutes. Create a fresh offer

**Auth code flow login doesn't work:**
- The login form is at `/auth/login`. Make sure Caddy routes `/auth/*` to the OAuth2 AS
- Credentials: `testuser` / `testpass`

**Rebuild after code changes (dev):**
```bash
docker compose down
./start-dev.sh     # rebuilds fat JARs + images, then starts
```

**Switch between dev and published-image flows:**
```bash
docker compose down
./start.sh         # uses sphereon/idk-*:${IDK_VERSION} from Docker Hub
```
