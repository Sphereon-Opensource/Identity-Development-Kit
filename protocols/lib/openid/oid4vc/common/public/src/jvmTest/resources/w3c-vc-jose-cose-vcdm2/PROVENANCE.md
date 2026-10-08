# W3C VC JOSE COSE test vectors

Source repository: https://github.com/w3c/vc-jose-cose-test-suite
Pinned commit: `ff6be51dc4d3c373dfaffa9a634cf751faf5285a`
Source paths:

- `tests/input/credential-jose-minimal.txt` (mapping test 6, successful JWT VC verification)
- `tests/input/credential-issuer-match-signed.txt` (mapping test 8, issuer claim matches the
  credential issuer)
- `tests/input/credential-jose-unknown-extensions.txt` (mapping test 9, unknown JOSE extensions)
- `tests/input/credential-jose-bad-signature.txt` (mapping test 12, invalid JWT signature)
- `tests/input/credential-jose-bad-media-type.txt` (mapping test 13, invalid JOSE media type)
- `tests/input/credential-jose-vc-vp-claims.txt` (mapping test 15, forbidden `vc`/`vp` claims)
- `tests/input/presentation-jose-multiple.txt` (mapping test 7, successful JWT VP verification)
- `tests/input/presentation-jose-bad-credential.txt` (mapping test 16, invalid enveloped credential)
- `tests/input/credential-minimal.json` (mapping test 10, unsecured credential verification)
- `tests/input/presentation-single.json` (mapping test 11, unsecured presentation verification)

The fixtures are retained from the pinned upstream files. `SHA256SUMS` records SHA-256 over
the exact raw bytes checked in here, including any trailing newline.

The additional files copied for this slice are `credential-full.json`,
`credential-unknown-extensions.json`, `presentation-multiple.json`,
`presentation-jose-bad-media-type.txt`, and the official public-key fixtures
`vm-p256.json`, `vm-p384.json`, `vm-p521.json`, and `vm-ed25519.json`. The verifier test reads
only each key fixture's `publicKeyJwk`; the private test material remains present solely because
these are the official upstream fixtures.

The basic signed credential is an upstream historical anomaly: its `iat` is an ISO-8601 string,
not a JWT NumericDate. The test therefore proves its signature and admitted-key trust separately
and expects production VCDM/JOSE semantic classification to reject `iat`; no NumericDate rule is
weakened to accommodate it. Unknown extension members are not treated as invalid by this slice;
normative ignore behavior is covered only when the surrounding profile permits it.

The verifier JVM test source set is configured to consume this canonical resource directory
directly, so no duplicate fixture copy is maintained under oid4vp/verifier/impl.

Coverage note: this pinned suite contains VCDM 2 root JOSE payloads and
`EnvelopedVerifiableCredential` VP children, but no signed nested
`EnvelopedVerifiablePresentation` vector. The nested VP assertion in the JVM test is therefore a
bounded envelope-shape check around the real signed VP artifact; recursive verifier acceptance
and holder-binding remain an integration concern and are not represented as an official vector.

The pinned VP uses scalar JSON strings for each envelope `type` value. The vector test preserves
that representation; it does not normalize it to an array accepted by any current verifier helper.

Mapping cases 10 and 11 use the unsecured `credential-minimal.json` and `presentation-single.json`
inputs. Mapping case 9 is expected to fail in the upstream mapping, but its signed fixture has an
ES512/P-521 header and no unknown extension member; the Kotlin tests therefore do not claim that
fixture as evidence for the specification's unknown-extension ignore rule.
