# W3C RDF 1.1 N-Quads syntax corpus

This offline fixture set is pinned to the `w3c/rdf-tests` repository at commit
`5a4e219f8311618d2301dcc5eeeb713300326f3c` (2026-08-21). The upstream
manifest is retained verbatim as `manifest.ttl`; its 87 actions comprise 53
`rdft:TestNQuadsPositiveSyntax` cases and 34
`rdft:TestNQuadsNegativeSyntax` cases. The JVM harness executes those
categories separately and does not treat RDF evaluation or canonicalization
fixtures as syntax tests.

This slice intentionally covers RDF 1.1 N-Quads syntax only. RDF 1.2
N-Quads/RDF-star quoted-triple syntax is not part of this pinned corpus, and
RDF dataset evaluation and RDFC-1.0 expected-output vectors remain separate
test suites. The production RDFC-1.0 codec therefore continues to reject
quoted triples fail-closed.

Source manifest:
https://github.com/w3c/rdf-tests/blob/5a4e219f8311618d2301dcc5eeeb713300326f3c/rdf/rdf11/rdf-n-quads/manifest.ttl

Source files:
https://github.com/w3c/rdf-tests/tree/5a4e219f8311618d2301dcc5eeeb713300326f3c/rdf/rdf11/rdf-n-quads

SHA-256 digests of the 87 upstream action bytes are in `SHA256SUMS.txt`.
The `literal_ascii_boundaries.nq` action contains a raw U+0000 byte, which
cannot be represented safely in a text patch; it is stored losslessly as
`literal_ascii_boundaries.nq.b64` and decoded by the harness before parsing.
All other actions retain their upstream UTF-8 bytes as `.nq` resources.

The upstream repository's license is included in `LICENSE-W3C-rdf-tests.md`.
