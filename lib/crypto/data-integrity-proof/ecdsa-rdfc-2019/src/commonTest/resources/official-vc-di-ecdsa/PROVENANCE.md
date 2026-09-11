# W3C ECDSA RDFC vectors

The vectors in `vectors.json` are copied from the normative W3C VC Data
Integrity ECDSA Cryptosuites Recommendation, Appendix A.1 and A.3:

* https://www.w3.org/TR/2025/REC-vc-di-ecdsa-20250515/#representation-ecdsa-rdfc-2019-with-curve-p-256
* https://www.w3.org/TR/2025/REC-vc-di-ecdsa-20250515/#representation-ecdsa-rdfc-2019-with-curve-p-384

The Recommendation states that these vectors use deterministic ECDSA and
that signatures are raw IEEE P1363 (`r || s`) values. The P-256 vector uses
SHA-256 for both RDFC-1.0 blank-node hashing and proof/document hashing; the
P-384 vector uses SHA-384 for both, as required by section 3.2.4.

This resource is test-only. The included secret key material is public W3C
test-vector material and must never be used outside conformance tests.
