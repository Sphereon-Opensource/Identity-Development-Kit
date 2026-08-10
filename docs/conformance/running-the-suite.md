# Running the OIDF Conformance Suite Against the IDK OID4VC Demo

> **Current manual diagnostic path.** This guide documents the existing
> operator-driven setup; it is not the repeatable CI completion gate. The
> tunnel-free, pinned-suite replacement and the required issuer/verifier/wallet
> matrix are defined in [`oidf-ci-strategy.md`](oidf-ci-strategy.md). A manual
> UI result or the existing single wallet smoke cannot establish full
> conformance.

Operator-facing how-to. Covers everything from a clean checkout to a green
test plan. Pair it with the live status in
[`oid4vc-coverage.md`](oid4vc-coverage.md) and the gap-fix tracker at
[`oid4vc-fixes.md`](oid4vc-fixes.md).

## Prerequisites

| Tool | Why | Notes |
| --- | --- | --- |
| Docker | runs the four-container demo stack | tested with Docker 29.x — see CLAUDE.md TestContainers note for WSL2 quirks |
| JDK 21 | builds the IDK fat JARs (only `start-dev.{sh,bat}`; `start.{sh,bat}` pulls published images) | matches `vdx/edk/idk/gradle.properties` |
| keytool + openssl | keystore generation + cert thumbprint capture | already on JDK; openssl ships with most Linux/macOS, MSYS, or via Chocolatey on Windows |
| Internet-reachable URL for the demo | the OIDF suite acts as a wallet/RP and must reach the demo | use ngrok (`ngrok http 8080`) or expose your LAN IP |
| OIDF conformance suite source + a running suite instance | the test runner | clone `https://gitlab.com/openid/conformance-suite` (the public IDK working copy is at `D:\git\conformance` on the maintainer's box). Run it locally per its README — the suite's UI lives at `https://localhost.emobix.co.uk:8443` by default. |

## Quick start

From a clean checkout of `idk/`:

```sh
cd examples/oid4vc/services
./start-dev.sh https://my.ngrok.app haip      # HAIP combo (x509_hash verifier + HAIP AS)
```

Or with published images (no Gradle):

```sh
./start.sh https://my.ngrok.app haip
```

Then in the OIDF conformance suite UI:

1. Pick the test plan you want to run (see the table below).
2. When the suite asks for the issuer / verifier endpoints, paste the URLs
   the demo printed at startup — for HAIP runs:
   - Issuer identifier: `https://my.ngrok.app/oid4vci`
   - Issuer metadata: `https://my.ngrok.app/.well-known/openid-credential-issuer/oid4vci`
   - AS issuer: `https://my.ngrok.app/auth`
   - AS discovery: `https://my.ngrok.app/.well-known/oauth-authorization-server/auth`
3. Run.

## Profile selection (second `start-dev` argument)

The OIDF conformance plans test different `client_id` prefixes for the
verifier and a HAIP-shaped vs plain AS. Pass the right profile name as the
second arg to `start-dev.{sh,bat}` (or `start.{sh,bat}`) and the script
layers the matching `--env-file` flags onto the `docker compose up`
invocation:

| Arg | Verifier prefix | AS shape | Use when |
| --- | --- | --- | --- |
| _omitted_ / `default` | `did:jwk` | plain | Regular demo. Same as before this work landed. |
| `did-jwk` | `did:jwk` (explicit) | plain | OID4VP plan with `did:jwk` cells; identical to `default`. |
| `x509-san-dns` | `x509_san_dns` | plain | OID4VP plan with `x509_san_dns` cells. |
| `x509-hash` | `x509_hash` | plain | OID4VP plan with `x509_hash` cells. |
| `haip` | `x509_hash` | HAIP | OID4VP HAIP test plan **and** OID4VCI HAIP test plan. |

Bogus arg values exit non-zero with a usage message listing the allowed
set. Switching profiles requires a container restart for the verifier (and
the AS, when the HAIP profile is involved); the issuer is unaffected.

If you need a non-preset combination — for example mixing the
`x509_san_dns` verifier profile with the HAIP AS profile — fall back to
the explicit form:

```sh
docker compose \
    --env-file profiles/conformance-x509-san-dns.env \
    --env-file profiles/conformance-haip-as.env up -d
```

## Trust anchors for mDoc presentations (IACA import)

The verifier's IssuerAuth chain check runs through `X509VerifyService` with
profile `ISO_18013_5` (`lib/mdoc/core/impl/.../IssuerAuthValidationImpl.kt`,
step 1 of ISO 18013-5 §9.3.1). When the OIDF conformance suite signs its
mock-wallet mdoc presentations with its own IACA cert, that root must be
configured as a trust anchor for the verifier — otherwise every IssuerAuth
chain fails validation with `X509VerificationResult.error=true`.

X.509 trust anchors live in the shared `lib/trust/x509` framework — the
same loader feeds `X509TrustValidationService` (used by every credential
format that binds via `x5c`) and the OID4VP mDoc IACA path. Configuration
keys are under `trust.anchors.x509.*`:

| Key                                              | Source                                       |
| ------------------------------------------------ | -------------------------------------------- |
| `trust.anchors.x509.enabled`                     | Set to `true` to enable the X.509 trust path. The demo turns it on by default. |
| `trust.anchors.x509.ca-bundle-paths.<n>`         | Indexed list of PEM file paths inside the container. Each file may contain one or more concatenated CERTIFICATE blocks. |
| `trust.anchors.x509.ca-bundle-urls.<n>`          | Indexed list of HTTP(S) URLs hosting PEM bundles, fetched and cached at runtime. |
| `trust.anchors.x509.trusted-fingerprints.<n>`    | Optional pinning by SHA-1 cert fingerprint or JWK thumbprint. |

In the demo's docker-compose stack the first path defaults to
`/app/trust-anchors/iaca.pem`, which is volume-mounted from
`services/trust-anchors/` on the host. Override via the
`VERIFIER_TRUST_X509_CA_BUNDLE_PATH` env var if you need a different path.

HAIP recognises three trust paths total: X.509 (above), DIDs
(`trust.anchors.did.*`, used for SD-JWT VC issued under did:web etc.), and
OpenID Federation (`trust.anchors.oidfed.*`, used for federation-discovered
issuers). The mDoc path is X.509-only because ISO 18013-5 §9.3.1 mandates
it; the other paths come into play for `sd_jwt_vc` and federation flows
through the same `TrustValidationService` framework.

### Importing the OIDF suite's IACA

1. In the OIDF conformance suite UI, open the `iso_mdl` test plan you
   intend to run. The suite shows its IACA certificate on the plan setup
   screen.
2. Save the certificate (or chain) as `services/trust-anchors/iaca.pem`.
   The file is git-ignored — it's environment-specific. Format is one or
   more concatenated PEM blocks:
   ```
   -----BEGIN CERTIFICATE-----
   <base64-encoded DER>
   -----END CERTIFICATE-----
   ```
3. Restart the verifier container so the config reload picks the file up:
   ```sh
   docker compose restart oid4vp-verifier
   ```
4. Confirm the load: `X509TrustAnchorLoader` logs
   `"Loaded N certificates from /app/trust-anchors/iaca.pem"` on the
   first request that triggers the X.509 trust path.

Without anchors configured the verifier rejects every IssuerAuth chain —
the right behaviour for production, since silently accepting an unknown
issuer would defeat the trust model.

For the `did:jwk` and `sd_jwt_vc` profiles, no trust-anchor work is
needed; mDoc IACA only matters for the `iso_mdl` test cells.

### Multiple anchors / rotation

If the suite rotates IACAs or you run several plans against different
issuers, concatenate the PEM blocks into `iaca.pem`, or drop a separate
file (e.g. `extra-issuer.pem`) and point the env var at it. The IDK reads
the entire file and treats every `-----BEGIN CERTIFICATE-----` block as
an additional trust anchor.

## Test-plan → profile mapping

| OIDF test plan | Profile arg | Notes |
| --- | --- | --- |
| `VP1FinalVerifierHappyFlow` (sd_jwt_vc) | `did-jwk`, `x509-san-dns`, `x509-hash` | Run against each prefix separately. |
| `VP1FinalVerifierHappyFlow` (iso_mdl) | `did-jwk` (smoke), `x509-hash` (HAIP) | Requires Fix 4 mdoc verification, which is in. Trust anchor (above) needed for the IACA. |
| `VP1FinalVerifierRequestUriMethodPost` | any verifier prefix | Validates the `wallet_nonce` echo on POST. Fix 1 closes this. |
| `VP1FinalVerifierTestPlan` (matrix) | `did-jwk`, `x509-san-dns`, `x509-hash` separately | Cover all three cells; the matrix is variant-driven. |
| `VP1FinalVerifierTestPlanHaip` | `haip` | Locked to `x509_hash` + encrypted `direct_post.jwt`. |
| `VCIIssuerHappyFlow` | `default` (plain AS) | sd_jwt_vc + mso_mdoc both supported. |
| `VCIIssuerTestPlan` | `default` | Full matrix with the plain AS. |
| `VCIIssuerTestPlanHaip` | `haip` | HAIP combo (DPoP + attestation + PKCE). |

## Inspecting failures

When a test plan fails, the four most useful sources of evidence are:

1. **The OIDF suite UI** — top-level pass/fail per condition with full
   request/response bodies attached.
2. **The webapp's "Show details" panel** (Phase A.5) — open the issuer or
   verifier page mid-flow, click `Show details`, and you get the raw JSON
   of the most recent issuer offer or verifier session, including any
   error fields. Faster than digging through container logs for one-off
   diagnoses.
3. **Container logs**:
   ```sh
   docker compose logs -f oid4vci-issuer oid4vp-verifier oauth2-as caddy
   ```
   Issuer and AS log structured JSON with command IDs; the verifier logs
   per-command outcomes; Caddy logs the routing layer (CORS preflights,
   404s on misrouted endpoints).
4. **The OIDF condition source** at
   `D:/git/conformance/src/main/java/net/openid/conformance/{vci10issuer,vp1finalverifier}/`.
   Each condition class has a one-line comment naming the spec section it
   enforces; reading the failing condition tells you exactly what shape
   the test expected.

## Known limitations

Live status lives in [`oid4vc-coverage.md`](oid4vc-coverage.md). Current
notable items (as of writing):

- **Reader-engagement (BLE/NFC) mDoc verification** in
  `MdocReaderEngagementManagerImpl` is still a placeholder. The OID4VP
  path goes through `VerifyHolderBindingCommandImpl` which IS fully
  verified — only the BLE/NFC engagement path is left as a footgun
  comment for the next maintainer.
- **Signed AS metadata** (`Accept: application/jwt` on
  `/.well-known/oauth-authorization-server`) is intentionally not
  produced. RFC 8414 §2 marks `signed_metadata` OPTIONAL and the OIDF
  suite does not probe it for the AS. The data class carries a passive
  deserialiser slot so third-party AS metadata that does carry one
  round-trips cleanly.
- **OIDF suite IACA import** is manual: drop the cert into
  `services/trust-anchors/iaca.pem` and restart the verifier
  container. Not yet automated by `start-dev`.
- **End-to-end mDoc round-trip test** (holder sign via
  `MdocOid4vpServiceImpl` → verifier verify via
  `VerifyHolderBindingCommandImpl`) is on the follow-ups list as
  regression coverage. Unit-level placeholder-removal and
  ingress-validation tests are in.

## Cross-references

- Audit + status: [`oid4vc-coverage.md`](oid4vc-coverage.md) — living
  checklist of every conformance condition we tracked, with file:line
  evidence for ✅/❓/❌ rows.
- Gap-fix tracker: [`oid4vc-fixes.md`](oid4vc-fixes.md) — short summary of
  the four targeted fixes (Fix 3 dropped, Fix 5 = this doc).
- Profile harness:
  [`../../examples/oid4vc/services/profiles/README.md`](../../examples/oid4vc/services/profiles/README.md).
- Demo services overview:
  [`../../examples/oid4vc/services/README.md`](../../examples/oid4vc/services/README.md).
