# IDK OID4VC Conformance Coverage

> Operator-facing how-to (clean checkout → green test plan):
> [`running-the-suite.md`](running-the-suite.md). Gap-fix tracker:
> [`oid4vc-fixes.md`](oid4vc-fixes.md).

Living document. Tracks IDK's coverage of the OpenID Foundation conformance
test suites for OpenID4VCI 1.0 final + HAIP 1.0 final (issuer / AS) and
OpenID4VP 1.0 final + HAIP 1.0 final (verifier).

Update each row as items are verified, fixed, or de-scoped. Don't bake the
date into the title — there is one canonical coverage doc, not a series.

Severity: **P0** = condition that produces FAILURE in the conformance suite.
**P1** = condition that produces WARNING. Status: ✅ verified, ❓ unclear (needs
runtime check or partial implementation), ❌ missing.

Audit baseline: 2026-04-29, conformance test sources at
`D:\git\conformance\src\main\java\net\openid\conformance\{vci10issuer,vp1finalverifier}`.

## Summary

| Surface | P0 ✅ | P0 ❓ | P0 ❌ | P1 ✅ | P1 ❓ | P1 ❌ |
| --- | --- | --- | --- | --- | --- | --- |
| Issuer (OID4VCI 1.0 final + HAIP) | 12 | 0 | 0 | 3 | 0 | 0 |
| Verifier (OID4VP 1.0 final + HAIP) | 11 | 0 | 0 | 2 | 0 | 0 |
| AS (HAIP / OID4VCI) | 12 | 0 | 0 | 1 | 0 | 0 |

All P0 rows are ✅. A-P1-1 (signed AS metadata) was dropped — RFC 8414 §2
marks `signed_metadata` OPTIONAL, a direct grep across the OIDF conformance
suite turned up zero references to it for the AS, HAIP 1.0 final does not
require it, and we found no concrete consumer. The
`AuthorizationServerMetadata` data class still carries a passive
`signedMetadata: String?` field so we can deserialise a third-party AS that
chooses to publish one.

Outstanding follow-ups (out of scope, not gaps in coverage):

- Reader-engagement (BLE/NFC) `MdocReaderEngagementManagerImpl` placeholders
  remain untouched; out of scope for OID4VP conformance.

Closed since first audit:
- V-P0-5 (`wallet_nonce` echo on request_uri POST) — Konform-validated
  and copied into the JAR claim.
- A-P0-9 (HAIP-shaped AS auth methods) — env-profile harness +
  start-script second-arg integration shipped.
- mDoc IACA trust-anchor import — routed through the shared
  `lib/trust/x509` framework. Config keys are
  `trust.anchors.x509.{enabled,ca-bundle-paths,ca-bundle-urls,trusted-fingerprints}`
  per `lib/trust/core/public/.../config/TrustConfig.kt`. The previously
  proposed `MdocVerifierConfig` interface was rejected on review — trust
  is X.509 + DID + OIDF + ETSI in HAIP, and the existing trust framework
  already covers all four. The OID4VP verifier now injects
  `X509TrustAnchorLoader` (extracted from
  `X509TrustValidationService.loadTrustedCerts`, so the same loader feeds
  generic chain validation and the mdoc IACA path) and passes the PEMs
  into `MdocValidations.fromDocument(trustedCerts = …)`. Demo wires
  `services/trust-anchors/` as a docker volume mount and exposes the
  path via `VERIFIER_TRUST_X509_CA_BUNDLE_PATH`. Operator import steps in
  `running-the-suite.md`.
- V-P0-11 (mDoc DeviceAuth + SessionTranscript verification) — real
  validators wired (see V-P0-11 row below). End-to-end regression test
  at `lib/mdoc/core/public/.../MdocVerificationE2ETest.kt` covers the
  happy path plus four SessionTranscript-mismatch negatives and one
  tampered-IssuerSignedItem case (six cells, all pass on JVM). The
  round-trip test caught a real bug in `DeviceAuthValidationImpl`:
  the holder signs with `withEncodePayloadAsDataItem(true)`, which
  wraps the payload in a tag-24 `encoded-cbor-data-item` bstr before
  computing the COSE Sig_structure. The verifier's re-attached payload
  now mirrors that wrapping
  (`CborEncodedItem(payload, payload).value.toBstr()`); without it,
  every device-signature verification returned "Signature invalid"
  even for valid presentations.

## Issuer (OID4VCI 1.0 final + HAIP)

### P0

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| I-P0-1 | `/credential` accepts `proofs.jwt` as an array (1.0 final wire shape, not legacy `proof` singular) | ✅ | `lib/openid/oid4vci/common/public/.../CredentialRequest.kt:67-100` `CredentialRequestProofs(proofType, proofValues: List<JsonElement>)` — serializer at `.../CredentialRequestProofsSerializer.kt:49-62` validates exactly one proof-type key with array value |
| I-P0-2 | `/credential` accepts `proofs.attestation` array for key attestation | ✅ | `CredentialRequest.kt:89-91` `CredentialRequestProofs.attestation(...)` factory; same array shape |
| I-P0-3 | `/nonce` endpoint mints fresh `c_nonce` and returns `c_nonce_expires_in` (HAIP-mandatory) | ✅ | `services/oid4vci-issuer/rest/.../IssueNonceEndpointCommand.kt:45-88` — `POST /nonce`, response model `NonceResponse(cNonce, cNonceExpiresIn?)`, `Cache-Control: no-store` |
| I-P0-4 | AS supports `client_attestation` token-endpoint auth with `OAuth-Client-Attestation` + `OAuth-Client-Attestation-PoP` headers | ✅ | See AS row A-P0-3 |
| I-P0-5 | DPoP-bound tokens validated end-to-end (PAR → token → credential) | ✅ | See AS row A-P0-1 |
| I-P0-6 | AS supports PAR (RFC 9126) | ✅ | See AS row A-P0-5 |
| I-P0-7 | AS supports `authorization_details` of type `openid_credential` | ✅ | `lib/openid/oid4vci/common/public/.../SupportingTypes.kt:92-98` `Oid4vciAuthorizationDetail(type = "openid_credential", credentialConfigurationId, ...)`; AS reflects in token response (see A-P0-6) |
| I-P0-8 | Format string is `dc+sd-jwt` (1.0 final), not deprecated `vc+sd-jwt` | ✅ | `examples/oid4vc/services/config/oid4vci-issuer.yml:36,61,107` all `format: "dc+sd-jwt"`; no `vc+sd-jwt` in non-test code paths |
| I-P0-9 | `credential_response_encryption` JWE supported, `zip=DEF` compression | ✅ | `lib/openid/oid4vci/issuer/impl/.../CredentialResponseEncryptor.kt:58-127` builds JWE compact via `CreateJweCompactArgs`; `RequestedCredentialResponseEncryption.zip` at `CredentialRequest.kt:131` |
| I-P0-10 | `credential_request_encryption` accepted; `encryption_required=true` enforced | ✅ | `CredentialIssuerMetadata.kt` `MetadataCredentialRequestEncryption.encryptionRequired`; `HandleCredentialEndpointCommand.decryptRequestIfNeeded()` consumes `application/jwt` request bodies |
| I-P0-11 | Signed issuer metadata via `Accept: application/jwt` | ✅ | `services/oid4vci-issuer/rest/.../GetIssuerMetadataEndpointCommand.kt:57-107` — Accept header negotiation between JSON and JWT, signed via `buildSignedMetadataCommand` |
| I-P0-12 | Six error codes returned with HTTP 400 + JSON body: `invalid_proof`, `invalid_nonce`, `unknown_credential_configuration`, `unknown_credential_identifier`, `invalid_encryption_parameters`, `credential_request_denied` | ✅ | `services/oid4vci-issuer/rest/.../Oid4vciProtocolUtils.kt:18-29` mapping; constants in `SupportingTypes.kt:71-73` |

### P1

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| I-P1-1 | `/deferred_credential` returns HTTP 202 with `interval` while pending | ✅ | `services/oid4vci-issuer/rest/.../HandleDeferredCredentialEndpointCommand.kt:53-151` |
| I-P1-2 | `/notification` accepts `credential_accepted` / `credential_failure` / `credential_deleted` | ✅ | `services/oid4vci-issuer/rest/.../HandleNotificationEndpointCommand.kt`; `SupportingTypes.kt:57-68` `CredentialNotificationEvent` enum |
| I-P1-3 | All metadata URLs HTTPS (issuer rejects `http://` identifiers) | ✅ | `lib/openid/oid4vci/issuer/impl/.../BuildIssuerMetadataCommandImpl.kt:51-64,102-132` — `isAllowedIssuerUrl` rejects non-HTTPS at metadata-build time with HTTP 400, except for an explicit private-network whitelist (`localhost`, `127.0.0.1`, `[::1]`, RFC 1918 ranges). |

### Issuer YAML cross-checks

| Check | Status | Notes |
| --- | --- | --- |
| Every credential config has `scope` (HAIP) | ✅ | `oid4vci-issuer.yml`: TestCredential `test_credential` (line 38), EuPid `eu_pid` (63), AgeOver18 `age_verification` (107) |

## Verifier (OID4VP 1.0 final + HAIP)

### P0

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| V-P0-1 | DCQL only on the wire — no `presentation_definition` emitted | ✅ | `lib/openid/oid4vp/verifier/impl/.../BuildAuthorizationRequestUriCommandImpl.kt:174-185` only emits `dcql_query`; comment "DCQL query (OpenID4VP 1.0 Final - NOT presentation_definition!)" |
| V-P0-2 | `client_id` prefix appears inside the value (e.g. `x509_san_dns:dns-name`); legacy `client_id_scheme` query parameter never emitted | ✅ | `BuildAuthorizationRequestUriCommandImpl.kt:229-235` `.let` guard — `client_id_scheme` is only included if explicitly set, and `CreateAuthorizationRequestCommandImpl.kt:137-158` substitutes the prefix into `client_id` |
| V-P0-3 | No `scope`, no `redirect_uri` in the auth request (`response_uri` instead) | ✅ | `BuildAuthorizationRequestUriCommandImpl.kt:154-236` no `scope`; `CreateAuthorizationRequestCommandImpl.kt:182-191` sets `redirectUri = null` for direct_post modes |
| V-P0-4 | `response_mode` both `direct_post` and `direct_post.jwt` work end-to-end | ✅ | `lib/openid/oid4vp/common/public/.../ResponseMode.kt:56-66` enum; `lib/oauth2/common/impl/.../VerifyJarmResponseCommandImpl.kt:118-194` JWE+JWS handling; `services/oid4vp-verifier/rest/.../UniversalOid4vpE2ETest.kt` E2E coverage |
| V-P0-5 | `request_uri_method=post` flow with `wallet_nonce` echo (OID4VP-1FINAL-5.10) | ✅ | `RequestUriHandlerImpl.kt:65-69,93-100` — the captured `walletNonce` is now Konform-validated (RFC 3986 unreserved set, length 8..512) and copied into the JAR's `additionalParameters` as a `wallet_nonce` claim before signing on the POST path. GET path passes `walletNonce = null` so the claim is absent (byte-identical to today). Covered by `RequestUriHandlerImplTest.kt` (positive POST echo, GET no-claim, length and charset rejection). |
| V-P0-6 | Request object `typ: oauth-authz-req+jwt`; `x5c` JWS header for `x509_san_dns` and `x509_hash` modes | ✅ | `lib/oauth2/client/impl/.../CreateSignedJarCommandImpl.kt:139-170,227` `JAR_JWT_TYP = "oauth-authz-req+jwt"`; lines 147-155 emit `x5c` chain |
| V-P0-7 | HAIP encrypted responses: minimum `ECDH-ES+A128KW` + `A128CBC-HS256`; all `client_metadata.jwks` keys have `kid` | ✅ | Algorithm support: `lib/crypto/core/public/.../KmsProviderCapabilities.kt:365-393` `KeyAgreementAlgorithm.ECDH_ES_A128KW` + `ContentEncryptionAlgorithm.A128CBC_HS256` enums plus 192/256 variants and ECDH-ES direct. `lib/crypto/core/impl/.../DecryptJweCommandImpl.kt:145-189` performs ECDH-ES key derivation, optional A128/192/256KW unwrap, then content decryption. Unsupported algs return `IdkError` ("Unsupported content encryption algorithm" / "Unsupported key encryption algorithm") wrapped to HTTP 400 in `VerifyJarmResponseCommandImpl.kt:141`. `kid` handling: `lib/crypto/core/public/.../Key.kt:659,805` derives `kid` from the key (RFC 7638 thumbprint or stored kid) on every `KeyInfo`/`ResolvedKeyInfo` round-trip — emitted JWKs always carry `kid`. |
| V-P0-8 | DCQL `meta` field names match 1.0 final exactly: `vct_values` (SD-JWT VC), `doctype_value` (mDoc) | ✅ | `lib/openid/oid4vp/dcql/.../DcqlFormatMeta.kt:73-100` `SdJwtVcMeta.vct_values: List<String>?` and `MdocMeta.doctype_value: String?` |
| V-P0-9 | Nonce: invalid-char rejection (FAILURE), min entropy/length (WARNING) | ✅ | The verifier's outbound nonce — which is what conformance `CheckForInvalidCharsInNonce` extracts from the auth request — is generated at `lib/openid/oid4vp/universal/impl/.../CreateAuthRequestServiceCommandImpl.kt:249-260` as 32 random bytes hex-encoded (64 chars from `0-9a-f`), a strict subset of the RFC 3986 unreserved set. 256 bits of entropy clears the entropy/length warnings. The verifier never validates inbound nonce charset (the only inbound nonce-shaped value is `wallet_nonce`, which is dropped — see V-P0-5), but conformance does not test that path. |
| V-P0-10 | SD-JWT KB-JWT validation: signature, nonce, aud, sd_hash, iat window (negatives → 4xx) | ✅ | `lib/openid/oid4vp/verifier/impl/.../VerifyHolderBindingCommandImpl.kt:131-177` calls `verifySdJwtCommand` and asserts `signatureValid && nonceValid && audienceValid && sdHashValid` |
| V-P0-11 | mDoc session transcript validation: device nonce match; encrypted variant for direct_post.jwt | ✅ | Wired through four deliverables: (a) `IssuerAuthValidationImpl.verifyDigests` at `lib/mdoc/core/impl/.../IssuerAuthValidationImpl.kt` now hashes each disclosed `IssuerSignedItem` under the MSO `digestAlgorithm` and matches against `mso.valueDigests[ns][digestID]`; (b) new `DeviceAuthValidation` interface (`lib/mdoc/core/public/.../data/DeviceAuthValidation.kt`) and impl (`lib/mdoc/core/impl/.../data/DeviceAuthValidationImpl.kt`) reconstruct the OID4VP SessionTranscript from `clientId`/`responseUri`/`mdocGeneratedNonce`/auth-request-`nonce`, re-attach the holder's detached payload (including the holder's `withEncodePayloadAsDataItem(true)` tag-24 wrapping), and verify the COSE_Sign1 signature against the device key from `mso.deviceKeyInfo.deviceKey` via `coseCryptoService.verify1`; (c) `VerifyHolderBindingCommandImpl.verifyMdocHolderBinding` now CBOR-decodes the DeviceResponse, runs `MdocValidations.fromDocument(...)` (cert chain + IssuerAuth COSE_Sign1 + validity + docType + digests) plus `DeviceAuthValidation.verifyDeviceAuth(...)` per document, and surfaces failures as `verified=false` with structured `errors` (mapped to HTTP 4xx upstream); (d) trust anchors come from the shared `lib/trust/x509` framework via `X509TrustAnchorLoader` (config keys `trust.anchors.x509.ca-bundle-paths` / `ca-bundle-urls` / `trusted-fingerprints`); the same loader feeds `X509TrustValidationService` so generic chain validation and the mdoc IACA path share one config surface. `VerifyHolderBindingArgs` carries optional `clientId` / `responseUri` / `mdocGeneratedNonce`; missing context fails fast. COSE_Mac0 is rejected (no shared secret in OID4VP JARM direct_post). Reader-engagement placeholders in `MdocReaderEngagementManagerImpl` are explicitly noted as out-of-scope for OID4VP and left as footgun comments pointing at the new validators. |

### P1

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| V-P1-1 | `client_metadata.vp_formats` populated for both formats | ✅ | `lib/openid/oid4vp/common/public/.../ClientMetadata.kt:161-199` `vpFormats: Map<String, VpFormatInfo>?` |
| V-P1-2 | Minimal `cnf.jwk` accepted (kty/crv/x/y for EC; no required kid/use/alg/key_ops/x5c) | ✅ | `VerifyHolderBindingCommandImpl.kt:182-191` extracts `cnf.jwk` from SD-JWT payload; SD-JWT lib enforces minimal structure |

## OAuth2 AS (HAIP / OID4VCI)

### P0

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| A-P0-1 | DPoP end-to-end: token endpoint validates proof, `/authorize` accepts `dpop_jkt`, PAR accepts `DPoP` header, access token carries `cnf.jkt` | ✅ | Token: `lib/oauth2/server/authorization/impl/.../HandleTokenRequestCommandImpl.kt:111` `verifyDpopProofIfPresent(...)`; authorize: `ParseAuthorizationRequestCommandImpl.kt:152` `dpopJkt` parsed; access token: `CreateAccessTokenCommandImpl.kt:71,150` `cnf.jkt` claim |
| A-P0-2 | DPoP-Nonce flow: `use_dpop_nonce` on stale nonce, `DPoP-Nonce` header on 401 | ✅ | `lib/oauth2/server/authorization/impl/.../InMemoryDpopNonceManagerImpl.kt:58-85` (TTL 5min, rotate 1min, window 5); `HandleTokenRequestCommandImpl.kt:150-157` |
| A-P0-3 | Client attestation auth: parses `OAuth-Client-Attestation` + `-PoP` headers, validates attester signature, PoP signed by client instance key with `aud` = token endpoint | ✅ | `lib/oauth2/server/authorization/impl/.../VerifyAttestationClientAuthCommandImpl.kt:125-141,190-195`; `typ: oauth-client-attestation+jwt` validation; `ExtractedClientAuthentication.kt:90-93` precedence |
| A-P0-4 | Attestation challenge endpoint (optional, OAuth2-ATCA-8) with Cache-Control: no-store | ✅ | `services/oauth2-as/rest/.../AttestationChallengeHttpEndpointCommandImpl.kt` — present when attestation is enabled |
| A-P0-5 | PAR (RFC 9126): `/par` endpoint, advertised in metadata, `request_uri` binds to `/authorize` | ✅ | `lib/oauth2/server/authorization/impl/.../HandlePushedAuthorizationRequestCommandImpl.kt`; `BuildServerMetadataCommandImpl.kt:220` advertises `pushed_authorization_request_endpoint`; `ParseAuthorizationRequestCommandImpl.kt:102-121` resolves `urn:ietf:params:oauth:request_uri:*` |
| A-P0-6 | `authorization_details` of type `openid_credential` accepted in PAR/authorize/token, reflected in token response | ✅ | `ParseAuthorizationRequestCommandImpl.kt:224` parses param; `lib/oauth2/server/authorization/impl/.../PreAuthorizedCodeGrantHandlerImpl.kt:93-111` reflects in token response |
| A-P0-7 | PKCE S256 enforcement (HAIP-2.4) | ✅ | `ParseAuthorizationRequestCommandImpl.kt:156-188` parses & validates length; enforcement via `pkce.isRequired` feature flag in `VerifyAuthorizationRequestCommandImpl.kt` |
| A-P0-8 | ES256 signing alg supported and advertised | ✅ | `lib/oauth2/server/authorization/impl/.../KeyAlgorithmToJwsAlg.kt:38` `ECDSA_SHA256 -> "ES256"`; metadata `idTokenSigningAlgValuesSupported` derived from key |
| A-P0-9 | `client_secret_basic` not advertised when running HAIP-only profile | ✅ | `examples/oid4vc/services/profiles/conformance-haip-as.env` flips `OAUTH2_TOKEN_ENDPOINT_AUTH_METHODS=client_attestation`, `OAUTH2_DPOP_POLICY=REQUIRED`, `OAUTH2_DPOP_NONCE_REQUIRED=true`, `OAUTH2_ATTESTATION_POLICY=REQUIRED`. The example AS YAML at `services/config/oauth2-as.yml:32-38` reads each via `${env:NAME:default}` so non-HAIP runs preserve today's behaviour. The `start-{dev,}.{sh,bat}` scripts accept a profile name as their second positional arg (`haip` layers the HAIP AS + x509_hash verifier profiles together). |
| A-P0-10 | Both grant types advertised: `authorization_code` + `urn:ietf:params:oauth:grant-type:pre-authorized_code`; `tx_code` validated in pre-auth grant | ✅ | `BuildServerMetadataCommandImpl.kt:167-194` from config; `PreAuthorizedCodeGrantHandlerImpl.kt:71` `txCode = paParams.txCode` |
| A-P0-11 | `client_attestation` advertised in `token_endpoint_auth_methods_supported` (HAIP) | ✅ | `BuildServerMetadataCommandImpl.kt:200-211` operator-driven; ensure config includes `client_attestation` |
| A-P0-12 | JWT access tokens (RFC 9068): `iss`, `sub`, `aud`, `exp`, `iat`, `client_id`, `scope`, `cnf.jkt` for DPoP | ✅ | `CreateAccessTokenCommandImpl.kt:59-81` |
| A-P0-13 | Issuer URL handling: `${EXTERNAL_BASE_URL}/auth` resolved via `req.resolveBaseUrl()` (no direct `serverConfig.baseUrl` reads) | ✅ | `services/oauth2-as/rest/.../OAuth2HttpResponses.kt` `resolveBaseUrl()` with config fallback to `Host` + `X-Forwarded-Proto`; metadata uses resolved URL throughout |

### P1

| # | Item | Status | Evidence |
| --- | --- | --- | --- |
| A-P1-1 | Signed AS metadata via `Accept: application/jwt` | n/a — deferred | RFC 8414 §2 defines `signed_metadata` as OPTIONAL. A direct grep across the OIDF conformance suite (`/d/git/conformance/src/main/java/net/openid/conformance/`) returns **zero** matches for `signed_metadata` / `SignedMetadata`; the only signed-metadata test is `VCIIssuerMetadataSignedTest` (credential issuer, already supported). HAIP 1.0 final does not require it. No known wallet or RP requires it. The deserialiser in `AuthorizationServerMetadata` does have a passive `signedMetadata: String?` slot so we can parse a third-party AS that publishes one, but actively producing one on our AS adds no value until a concrete consumer asks. Reopen if a real consumer surfaces. |

## Outstanding work

Ordered by impact. Each item below maps to a coverage row above; close the
row when the underlying work lands.

1. **mDoc DeviceAuth / SessionTranscript verification** (V-P0-11) — the
   `verifyMdocHolderBinding()` stub at
   `VerifyHolderBindingCommandImpl.kt:206-239` must be replaced with real
   verification:
   - Parse CBOR `DeviceResponse` from the base64url presentation.
   - For each `Document`, verify the `IssuerAuth` `COSE_Sign1` against the
     issuing-authority cert chain.
   - Reconstruct the OID4VP `SessionTranscript` per ISO 18013-7 §B.4
     using `client_id`, `response_uri`, `nonce`, optional
     `mdoc_generated_nonce`, optional response-encryption material;
     produce both unencrypted and encrypted (direct_post.jwt) variants.
   - Verify `DeviceAuth` `COSE_Sign1` (or `COSE_Mac0`) against the device
     key from the MSO over that session transcript.
   - Check `validityInfo.validFrom` / `validUntil` / `signed`.
   - Match digest IDs in `IssuerSignedItems` against disclosed namespaces.
   - Reject with HTTP 4xx on any failure.
   Requires bringing the mdoc library (CBOR + COSE) into the IDK
   composite build — not currently present. Blocks all `iso_mdl` variants
   of OID4VP conformance.

2. **`wallet_nonce` echo on request_uri POST** (V-P0-5) — make the JAR
   round-trip the wallet-supplied `wallet_nonce` per OID4VP-1FINAL-5.10:
   - Stop dropping the parameter in `RequestUriHandlerImpl.kt:62-66`.
   - Add a `walletNonce` field to the `AuthorizationRequest` data class
     (or to a sibling JAR-only payload) so it can be set as a claim
     before signing.
   - Set the claim only when the caller used POST (the GET path must
     leave it absent).
   - The `requestForJar` copy at `RequestUriHandlerImpl.kt:85-89` must
     attach the wallet_nonce to the new request before
     `createSignedJarCommand.execute(...)`.
   - Optional: validate that walletNonce length and charset are sane on
     ingress (cheap defense, matches the verifier's own nonce policy).

3. **Operator config for HAIP** (A-P0-9) — wire the example
   `oauth2-as.yml` so `token-endpoint-auth-methods-supported` advertises
   `client_attestation` and **excludes** `client_secret_basic` for HAIP
   conformance runs. Cleanest option: a sibling profile fragment under
   `services/profiles/conformance-haip-as.env` that flips the relevant
   env vars, parallel to the verifier profile harness. Optional safety:
   a startup-time consistency guard that logs a warning when a
   HAIP-shaped feature set (DPoP enabled, attestation enabled, PKCE
   required) is paired with a non-HAIP auth methods list.

4. **Signed AS metadata** (A-P1-1) — bring the AS metadata endpoint to
   parity with the OID4VCI issuer. The issuer at
   `GetIssuerMetadataEndpointCommand.kt:87-154` already does
   Accept-header negotiation, returns `application/jwt` with a signed
   payload via `BuildSignedMetadataCommand`, and inlines a
   `signed_metadata` field in the JSON variant when a signing key is
   configured. Apply the same pattern to
   `OAuth2ServerMetadataHttpEndpointCommandImpl.kt`. The AS already has a
   keystore and a signing-key resolution path — this is wiring, not new
   crypto. RFC 8414 §3.1 explicitly defines the JWT-signed variant; HAIP
   wallets and any cautious RP can reasonably ask for it.

## How to update this document

- Tick a row when the implementation changes; cite the new file:line.
- Move closed items to a brief "Closed" log at the bottom of the relevant
  section if you want history; otherwise just flip ✅ — the git history is
  the audit trail.
- When a new conformance condition surfaces from an actual test run, add a
  row with severity P0/P1, status, and evidence.
- Keep the **Summary** counts at the top in sync.
