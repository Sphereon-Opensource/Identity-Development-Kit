# vc-di-eddsa provenance

`valid-vc.json` is the deterministic JSON form of the official generator input
`tests/vc-generator/validVc.js` from the W3C VC Data Integrity EdDSA test suite.

- Upstream: https://github.com/w3c/vc-di-eddsa-test-suite
- Pinned commit: `c022df28036426b8c0218fd01c2b0589dbb36d50`
- Upstream file: `tests/vc-generator/validVc.js`
- Upstream file SHA-256 (UTF-8 bytes): `bff7e14ab33531652e9206e08e6a949ec87a4e5bfd09e13e7b53754be504fb0a`
- Upstream license: BSD-3-Clause (see upstream `LICENSE.md`)
- Retrieved: 2026-08-26

The pinned upstream suite generates issuer/verifier data at runtime rather than
shipping static expected N-Quads/signature files. The Kotlin tests therefore
assert the normative deterministic transformation, two-SHA-256 hash framing,
and encoding invariants directly; no network access or runtime fixture download
is used.

`spec-example-rdfc-2022.json` is the static signed vector from Appendix B.1,
Examples 7-17 of the W3C Data Integrity EdDSA Cryptosuites specification:

- https://www.w3.org/TR/vc-di-eddsa/#representation-eddsa-rdfc-2022
- pinned source commit: https://github.com/w3c/vc-di-eddsa/commit/549929f78f65d60d0516e0757269bfaffb926847
- pinned upstream `index.html` SHA-256 (UTF-8 bytes): `1fcbd0a3809c7b3c4ee7833e2c770090c2f1feb87ae041ad271aadbbab8b7282`
- specification license: W3C Software and Document License (see `SPEC-LICENSE.md`)
