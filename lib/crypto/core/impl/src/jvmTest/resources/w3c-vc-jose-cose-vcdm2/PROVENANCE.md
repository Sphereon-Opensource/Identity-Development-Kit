# W3C VC JOSE COSE real-crypto test inputs

Source repository: https://github.com/w3c/vc-jose-cose-test-suite
Pinned commit: `ff6be51dc4d3c373dfaffa9a634cf751faf5285a`
Source paths:

- `tests/input/credential-jose-minimal.txt` (mapping test 6)
- `tests/input/credential-issuer-match-signed.txt` (mapping test 8)
- `tests/input/credential-jose-bad-signature.txt` (mapping test 12)
- `tests/input/vm-p256.json` (`publicKeyJwk` only)
- `tests/input/vm-ed25519.json` (`publicKeyJwk` only)

`credential-jose-minimal.txt` is copied byte-for-byte after trimming the upstream trailing
newline. `vm-p256-public-key.json` is the exact upstream `publicKeyJwk` object. The upstream
`secretKeyJwk` is deliberately not vendored; this test verifies the signed artifact with the
public key admitted through `VerifyJwsArgs.trustedJwks`.

`SHA256SUMS` records SHA-256 digests of the trimmed logical fixture contents, matching the
adjacent W3C vector resource convention.

The Ed25519 resource is derived from the upstream `publicKeyJwk` only; the upstream
`secretKeyJwk` is not vendored. The credential payload contains the upstream string `iat`. This is retained as-is so callers'
existing JWT semantic validation can continue to reject a non-NumericDate claim; this test only
asserts the production JWS cryptographic verification and trust-establishment flags.
